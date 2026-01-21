#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"
#include "../include/event.h"
#include <algorithm>
#include <atomic>
#include <iostream>
#include <mutex>
#include <sstream>
#include <thread>

// Global variables for thread communication
std::mutex mtx;
std::atomic<bool> shouldTerminate(false);
std::atomic<bool> isConnected(false);
ConnectionHandler *connectionHandler = nullptr;
StompProtocol *protocol = nullptr;

/**
 * Socket reading thread - reads frames from server and processes them
 */
void socketReaderThread() {
  while (!shouldTerminate && isConnected) {
    std::string frame;
    // Read STOMP frame (terminated by null character '\0')
    if (!connectionHandler->getFrameAscii(frame, '\0')) {
      std::lock_guard<std::mutex> lock(mtx);
      std::cout << "Disconnected from server" << std::endl;
      isConnected = false;
      shouldTerminate = true;
      break;
    }

    if (!frame.empty()) {
      std::lock_guard<std::mutex> lock(mtx);
      protocol->processFrame(frame);

      if (protocol->shouldTerminate()) {
        isConnected = false;
        shouldTerminate = true;
        break;
      }
    }
  }
}

/**
 * Parse host:port string into separate host and port
 */
bool parseHostPort(const std::string &hostPort, std::string &host,
                   short &port) {
  size_t colonPos = hostPort.find(':');
  if (colonPos == std::string::npos) {
    return false;
  }
  host = hostPort.substr(0, colonPos);
  try {
    port = static_cast<short>(std::stoi(hostPort.substr(colonPos + 1)));
  } catch (...) {
    return false;
  }
  return true;
}

/**
 * Tokenize a string by spaces
 */
std::vector<std::string> tokenize(const std::string &line) {
  std::vector<std::string> tokens;
  std::istringstream iss(line);
  std::string token;
  while (iss >> token) {
    tokens.push_back(token);
  }
  return tokens;
}

/**
 * Convert game name to channel format
 */
std::string toChannelName(const std::string &teamA, const std::string &teamB) {
  std::string channel = teamA + "_" + teamB;
  return channel;
}

int main(int argc, char *argv[]) {
  std::thread *readerThread = nullptr;

  std::cout << "STOMP World Cup Informer Client" << std::endl;
  std::cout << "Commands: login, join, exit, report, summary, logout"
            << std::endl;

  while (true) {
    const short bufsize = 1024;
    char buf[bufsize];
    std::cin.getline(buf, bufsize);
    std::string line(buf);

    if (line.empty()) {
      continue;
    }

    std::vector<std::string> tokens = tokenize(line);
    if (tokens.empty()) {
      continue;
    }

    std::string command = tokens[0];

    // ==================== LOGIN ====================
    if (command == "login") {
      if (tokens.size() < 4) {
        std::cout << "Usage: login {host:port} {username} {password}"
                  << std::endl;
        continue;
      }

      if (isConnected) {
        std::cout
            << "The client is already logged in, log out before trying again"
            << std::endl;
        continue;
      }

      std::string hostPort = tokens[1];
      std::string username = tokens[2];
      std::string password = tokens[3];

      std::string host;
      short port;
      if (!parseHostPort(hostPort, host, port)) {
        std::cout << "Invalid host:port format" << std::endl;
        continue;
      }

      // Create new connection handler and protocol
      connectionHandler = new ConnectionHandler(host, port);
      protocol = new StompProtocol();

      if (!connectionHandler->connect()) {
        std::cout << "Could not connect to server" << std::endl;
        delete connectionHandler;
        delete protocol;
        connectionHandler = nullptr;
        protocol = nullptr;
        continue;
      }

      // Send CONNECT frame
      std::string connectFrame =
          protocol->createConnectFrame(username, password);
      if (!connectionHandler->sendFrameAscii(connectFrame, '\0')) {
        std::cout << "Could not connect to server" << std::endl;
        delete connectionHandler;
        delete protocol;
        connectionHandler = nullptr;
        protocol = nullptr;
        continue;
      }

      // Wait for CONNECTED or ERROR response
      std::string response;
      if (!connectionHandler->getFrameAscii(response, '\0')) {
        std::cout << "Could not connect to server" << std::endl;
        delete connectionHandler;
        delete protocol;
        connectionHandler = nullptr;
        protocol = nullptr;
        continue;
      }

      // Process the response
      protocol->processFrame(response);

      if (protocol->shouldTerminate()) {
        // Error occurred during login
        delete connectionHandler;
        delete protocol;
        connectionHandler = nullptr;
        protocol = nullptr;
        continue;
      }

      // Successfully connected - start reader thread
      isConnected = true;
      shouldTerminate = false;
      readerThread = new std::thread(socketReaderThread);
    }

    //  join secition 
    else if (command == "join") {
      if (!isConnected) {
        std::cout << "Not connected. Please login first." << std::endl;
        continue;
      }

      if (tokens.size() < 2) {
        std::cout << "Usage: join {game_name}" << std::endl;
        continue;
      }

      std::string gameName = tokens[1];
      std::string subscribeFrame = protocol->createSubscribeFrame(gameName);

      std::lock_guard<std::mutex> lock(mtx);
      if (!connectionHandler->sendFrameAscii(subscribeFrame, '\0')) {
        std::cout << "Failed to send subscribe frame" << std::endl;
      }
    }

    //  exit section
    else if (command == "exit") {
      if (!isConnected) {
        std::cout << "Not connected. Please login first." << std::endl;
        continue;
      }

      if (tokens.size() < 2) {
        std::cout << "Usage: exit {game_name}" << std::endl;
        continue;
      }

      std::string gameName = tokens[1];
      std::string unsubscribeFrame = protocol->createUnsubscribeFrame(gameName);

      std::lock_guard<std::mutex> lock(mtx);
      if (!connectionHandler->sendFrameAscii(unsubscribeFrame, '\0')) {
        std::cout << "Failed to send unsubscribe frame" << std::endl;
      }
    }

    // Report section 
    else if (command == "report") {
      if (!isConnected) {
        std::cout << "Not connected. Please login first." << std::endl;
        continue;
      }

      if (tokens.size() < 2) {
        std::cout << "Usage: report {file}" << std::endl;
        continue;
      }

      std::string filePath = tokens[1];

      try {
        // Parse the events file
        names_and_events nae = parseEventsFile(filePath);
        std::string gameName = toChannelName(nae.team_a_name, nae.team_b_name);

        // Send each event as a SEND frame
        for (const Event &event : nae.events) {
          std::string sendFrame = protocol->createSendFrame(gameName, event, filePath);

          std::lock_guard<std::mutex> lock(mtx);
          if (!connectionHandler->sendFrameAscii(sendFrame, '\0')) {
            std::cout << "Failed to send event frame" << std::endl;
            break;
          }
        }
      } catch (const std::exception &e) {
        std::cout << "Error parsing events file: " << e.what() << std::endl;
      }
    }

    // summary section 
    else if (command == "summary") {
      if (tokens.size() < 4) {
        std::cout << "Usage: summary {game_name} {user} {file}" << std::endl;
        continue;
      }

      std::string gameName = tokens[1];
      std::string user = tokens[2];
      std::string filePath = tokens[3];

      std::lock_guard<std::mutex> lock(mtx);
      protocol->writeSummary(gameName, user, filePath);
    }

    // logout section 
    else if (command == "logout") {
      if (!isConnected) {
        std::cout << "Not connected. Please login first." << std::endl;
        continue;
      }

      std::string disconnectFrame = protocol->createDisconnectFrame();

      {
        std::lock_guard<std::mutex> lock(mtx);
        if (!connectionHandler->sendFrameAscii(disconnectFrame, '\0')) {
          std::cout << "Failed to send disconnect frame" << std::endl;
        }
      }

      // Wait for RECEIPT then close
      // The reader thread will handle the RECEIPT and set shouldTerminate
      if (readerThread != nullptr && readerThread->joinable()) {
        readerThread->join();
        delete readerThread;
        readerThread = nullptr;
      }

      connectionHandler->close();
      delete connectionHandler;
      delete protocol;
      connectionHandler = nullptr;
      protocol = nullptr;
      isConnected = false;
      shouldTerminate = false;

      std::cout << "Logged out successfully" << std::endl;
    }

    // unknown command section 
    else {
      std::cout << "Unknown command: " << command << std::endl;
      std::cout
          << "Available commands: login, join, exit, report, summary, logout"
          << std::endl;
    }

    // Check if we need to exit due to error
    if (shouldTerminate && !isConnected) {
      if (readerThread != nullptr && readerThread->joinable()) {
        readerThread->join();
        delete readerThread;
        readerThread = nullptr;
      }

      if (connectionHandler != nullptr) {
        connectionHandler->close();
        delete connectionHandler;
        connectionHandler = nullptr;
      }

      if (protocol != nullptr) {
        delete protocol;
        protocol = nullptr;
      }

      shouldTerminate = false;
    }
  }

  // Cleanup
  if (readerThread != nullptr && readerThread->joinable()) {
    readerThread->join();
    delete readerThread;
  }

  if (connectionHandler != nullptr) {
    connectionHandler->close();
    delete connectionHandler;
  }

  if (protocol != nullptr) {
    delete protocol;
  }

  return 0;
}