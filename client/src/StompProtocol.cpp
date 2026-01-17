#include "../include/StompProtocol.h"
#include <algorithm>
#include <fstream>
#include <iostream>
#include <sstream>

// Constructor
StompProtocol::StompProtocol()
    : isConnected(false), shouldTerminateFlag(false), username(""),
      subscriptionIdCounter(0), receiptIdCounter(0), subscriptionIdToChannel(),
      channelToSubscriptionId(), gameData(), pendingReceipts() {}

// Destructor
StompProtocol::~StompProtocol() {}

std::string StompProtocol::createConnectFrame(const std::string &user,
                                              const std::string &password) {
  this->username = user;
  std::stringstream ss;
  ss << "CONNECT\n";
  ss << "accept-version:1.2\n";
  ss << "host:stomp.cs.bgu.ac.il\n";
  ss << "login:" << user << "\n";
  ss << "passcode:" << password << "\n";
  ss << "\n"; // Empty line before body
  // No body for CONNECT
  return ss.str();
}

std::string StompProtocol::createSubscribeFrame(const std::string &channel) {
  int subId = subscriptionIdCounter++;
  int receiptId = receiptIdCounter++;

  // Store mapping
  subscriptionIdToChannel[subId] = channel;
  channelToSubscriptionId[channel] = subId;
  pendingReceipts[receiptId] = "SUBSCRIBE:" + channel;

  std::stringstream ss;
  ss << "SUBSCRIBE\n";
  ss << "destination:/" << channel << "\n";
  ss << "id:" << subId << "\n";
  ss << "receipt:" << receiptId << "\n";
  ss << "\n"; // Empty line before body (no body)
  return ss.str();
}

std::string StompProtocol::createUnsubscribeFrame(const std::string &channel) {
  auto it = channelToSubscriptionId.find(channel);
  if (it == channelToSubscriptionId.end()) {
    std::cerr << "Not subscribed to channel: " << channel << std::endl;
    return "";
  }

  int subId = it->second;
  int receiptId = receiptIdCounter++;
  pendingReceipts[receiptId] = "UNSUBSCRIBE:" + channel;

  std::stringstream ss;
  ss << "UNSUBSCRIBE\n";
  ss << "id:" << subId << "\n";
  ss << "receipt:" << receiptId << "\n";
  ss << "\n"; // Empty line before body (no body)
  return ss.str();
}

std::string StompProtocol::createSendFrame(const std::string &channel,
                                           const Event &event) {
  std::stringstream ss;
  ss << "SEND\n";
  ss << "destination:/" << channel << "\n";
  ss << "\n"; // Empty line before body

  // Body content - formatted as specified in assignment
  ss << "user: " << username << "\n";
  ss << "team a: " << event.get_team_a_name() << "\n";
  ss << "team b: " << event.get_team_b_name() << "\n";
  ss << "event name: " << event.get_name() << "\n";
  ss << "time: " << event.get_time() << "\n";

  ss << "general game updates:\n";
  for (const auto &pair : event.get_game_updates()) {
    ss << "    " << pair.first << ": " << pair.second << "\n";
  }

  ss << "team a updates:\n";
  for (const auto &pair : event.get_team_a_updates()) {
    ss << "    " << pair.first << ": " << pair.second << "\n";
  }

  ss << "team b updates:\n";
  for (const auto &pair : event.get_team_b_updates()) {
    ss << "    " << pair.first << ": " << pair.second << "\n";
  }

  ss << "description:\n";
  ss << event.get_discription();

  return ss.str();
}

std::string StompProtocol::createDisconnectFrame() {
  int receiptId = receiptIdCounter++;
  pendingReceipts[receiptId] = "DISCONNECT";

  std::stringstream ss;
  ss << "DISCONNECT\n";
  ss << "receipt:" << receiptId << "\n";
  ss << "\n"; // Empty line before body (no body)
  return ss.str();
}

void StompProtocol::processFrame(const std::string &frame) {
  if (frame.empty())
    return;

  // Parse command (first line)
  std::istringstream iss(frame);
  std::string command;
  std::getline(iss, command);

  // Remove carriage return if present
  if (!command.empty() && command.back() == '\r') {
    command.pop_back();
  }

  // Parse headers
  std::map<std::string, std::string> headers;
  std::string line;
  while (std::getline(iss, line)) {
    // Remove carriage return if present
    if (!line.empty() && line.back() == '\r') {
      line.pop_back();
    }

    if (line.empty()) {
      break; // Empty line marks end of headers
    }

    size_t colonPos = line.find(':');
    if (colonPos != std::string::npos) {
      std::string key = line.substr(0, colonPos);
      std::string value = line.substr(colonPos + 1);
      // Trim leading space from value
      if (!value.empty() && value[0] == ' ') {
        value = value.substr(1);
      }
      headers[key] = value;
    }
  }

  // Get body (rest of the frame after headers)
  std::stringstream bodyStream;
  while (std::getline(iss, line)) {
    bodyStream << line << "\n";
  }
  std::string body = bodyStream.str();
  // Remove trailing newline
  if (!body.empty() && body.back() == '\n') {
    body.pop_back();
  }

  // Handle different frame types
  if (command == "CONNECTED") {
    isConnected = true;
    std::cout << "Login successful" << std::endl;
  } else if (command == "MESSAGE") {
    handleMessage(headers, body);
  } else if (command == "RECEIPT") {
    handleReceipt(headers);
  } else if (command == "ERROR") {
    handleError(headers, body);
  }
}

void StompProtocol::handleMessage(
    const std::map<std::string, std::string> &headers,
    const std::string &body) {
  // Parse the message body to extract event information
  std::string user, teamA, teamB, eventName, description;
  int time = 0;
  std::map<std::string, std::string> gameUpdates, teamAUpdates, teamBUpdates;

  std::istringstream iss(body);
  std::string line;
  std::string currentSection = "";

  while (std::getline(iss, line)) {
    // Remove carriage return if present
    if (!line.empty() && line.back() == '\r') {
      line.pop_back();
    }

    if (line.find("user: ") == 0) {
      user = line.substr(6);
    } else if (line.find("team a: ") == 0) {
      teamA = line.substr(8);
    } else if (line.find("team b: ") == 0) {
      teamB = line.substr(8);
    } else if (line.find("event name: ") == 0) {
      eventName = line.substr(12);
    } else if (line.find("time: ") == 0) {
      time = std::stoi(line.substr(6));
    } else if (line == "general game updates:") {
      currentSection = "general";
    } else if (line == "team a updates:") {
      currentSection = "teamA";
    } else if (line == "team b updates:") {
      currentSection = "teamB";
    } else if (line == "description:") {
      currentSection = "description";
    } else if (currentSection == "description") {
      if (!description.empty())
        description += "\n";
      description += line;
    } else if (!line.empty() && line[0] == ' ') {
      // This is an update line
      size_t colonPos = line.find(':');
      if (colonPos != std::string::npos) {
        std::string key = line.substr(0, colonPos);
        // Trim leading spaces from key
        size_t start = key.find_first_not_of(" \t");
        if (start != std::string::npos) {
          key = key.substr(start);
        }
        std::string value = line.substr(colonPos + 1);
        // Trim leading space from value
        if (!value.empty() && value[0] == ' ') {
          value = value.substr(1);
        }

        if (currentSection == "general") {
          gameUpdates[key] = value;
        } else if (currentSection == "teamA") {
          teamAUpdates[key] = value;
        } else if (currentSection == "teamB") {
          teamBUpdates[key] = value;
        }
      }
    }
  }

  // Get destination/game channel from headers
  std::string destination;
  auto it = headers.find("destination");
  if (it != headers.end()) {
    destination = it->second;
    // Remove leading '/'
    if (!destination.empty() && destination[0] == '/') {
      destination = destination.substr(1);
    }
  }

  // Create event and store it
  Event event(teamA, teamB, eventName, time, gameUpdates, teamAUpdates,
              teamBUpdates, description);

  // Store in gameData: gameName -> user -> events
  gameData[destination][user].push_back(event);
}

void StompProtocol::handleReceipt(
    const std::map<std::string, std::string> &headers) {
  auto it = headers.find("receipt-id");
  if (it == headers.end())
    return;

  int receiptId = std::stoi(it->second);

  auto pendingIt = pendingReceipts.find(receiptId);
  if (pendingIt == pendingReceipts.end())
    return;

  std::string action = pendingIt->second;
  pendingReceipts.erase(pendingIt);

  if (action.find("SUBSCRIBE:") == 0) {
    std::string channel = action.substr(10);
    std::cout << "Joined channel " << channel << std::endl;
  } else if (action.find("UNSUBSCRIBE:") == 0) {
    std::string channel = action.substr(12);
    std::cout << "Exited channel " << channel << std::endl;

    // Remove from maps
    auto subIt = channelToSubscriptionId.find(channel);
    if (subIt != channelToSubscriptionId.end()) {
      int subId = subIt->second;
      subscriptionIdToChannel.erase(subId);
      channelToSubscriptionId.erase(subIt);
    }
  } else if (action == "DISCONNECT") {
    shouldTerminateFlag = true;
    isConnected = false;
  }
}

void StompProtocol::handleError(
    const std::map<std::string, std::string> &headers,
    const std::string &body) {
  auto it = headers.find("message");
  if (it != headers.end()) {
    std::cout << "Error: " << it->second << std::endl;
  }

  if (!body.empty()) {
    std::cout << body << std::endl;
  }

  shouldTerminateFlag = true;
  isConnected = false;
}

void StompProtocol::writeSummary(const std::string &gameName,
                                 const std::string &user,
                                 const std::string &filepath) {
  auto gameIt = gameData.find(gameName);
  if (gameIt == gameData.end()) {
    std::cout << "No data for game: " << gameName << std::endl;
    return;
  }

  auto userIt = gameIt->second.find(user);
  if (userIt == gameIt->second.end()) {
    std::cout << "No data from user: " << user << " for game: " << gameName
              << std::endl;
    return;
  }

  const std::vector<Event> &events = userIt->second;
  if (events.empty()) {
    std::cout << "No events for game: " << gameName << " from user: " << user
              << std::endl;
    return;
  }

  // Get team names from first event
  std::string teamA = events[0].get_team_a_name();
  std::string teamB = events[0].get_team_b_name();

  // Aggregate stats across all events
  std::map<std::string, std::string> generalStats;
  std::map<std::string, std::string> teamAStats;
  std::map<std::string, std::string> teamBStats;

  // Sort events by time
  std::vector<Event> sortedEvents = events;
  std::sort(sortedEvents.begin(), sortedEvents.end(),
            [](const Event &a, const Event &b) {
              return a.get_time() < b.get_time();
            });

  for (const Event &event : sortedEvents) {
    for (const auto &pair : event.get_game_updates()) {
      generalStats[pair.first] = pair.second;
    }
    for (const auto &pair : event.get_team_a_updates()) {
      teamAStats[pair.first] = pair.second;
    }
    for (const auto &pair : event.get_team_b_updates()) {
      teamBStats[pair.first] = pair.second;
    }
  }

  // Write to file
  std::ofstream outFile(filepath);
  if (!outFile.is_open()) {
    std::cout << "Could not open file: " << filepath << std::endl;
    return;
  }

  outFile << teamA << " vs " << teamB << "\n";
  outFile << "Game stats:\n";

  outFile << "General stats:\n";
  for (const auto &pair : generalStats) {
    outFile << "    " << pair.first << ": " << pair.second << "\n";
  }

  outFile << teamA << " stats:\n";
  for (const auto &pair : teamAStats) {
    outFile << "    " << pair.first << ": " << pair.second << "\n";
  }

  outFile << teamB << " stats:\n";
  for (const auto &pair : teamBStats) {
    outFile << "    " << pair.first << ": " << pair.second << "\n";
  }

  outFile << "Game event reports:\n";
  for (const Event &event : sortedEvents) {
    outFile << event.get_time() << " - " << event.get_name() << ":\n";
    outFile << event.get_discription() << "\n\n";
  }

  outFile.close();
  std::cout << "Summary written to " << filepath << std::endl;
}

bool StompProtocol::shouldTerminate() { return shouldTerminateFlag; }

const std::string &StompProtocol::getUsername() const { return username; }
