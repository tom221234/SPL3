#pragma once
#include "../include/ConnectionHandler.h"
#include "../include/event.h"
#include <map>
#include <string>
#include <vector>

// TODO: implement the STOMP protocol
class StompProtocol {
private:
  bool isConnected;
  bool shouldTerminateFlag;
  std::string username;
  int subscriptionIdCounter; // For generating unique subscription IDs
  int receiptIdCounter;      // For generating unique receipt IDs

  // Maps: subscription ID <-> game channel
  std::map<int, std::string> subscriptionIdToChannel;
  std::map<std::string, int> channelToSubscriptionId;

  // Game data storage: game_name -> user -> vector of Events
  std::map<std::string, std::map<std::string, std::vector<Event>>> gameData;

  // Pending receipts: receipt ID -> expected action
  std::map<int, std::string> pendingReceipts;

  // Helper methods for processing frames
  void handleMessage(const std::map<std::string, std::string> &headers,
                     const std::string &body);
  void handleReceipt(const std::map<std::string, std::string> &headers);
  void handleError(const std::map<std::string, std::string> &headers,
                   const std::string &body);

public:
  // Constructor and Destructor
  StompProtocol();
  virtual ~StompProtocol();

  // Create STOMP frames for each command
  std::string createConnectFrame(const std::string &username,
                                 const std::string &password);
  std::string createSubscribeFrame(const std::string &channel);
  std::string createUnsubscribeFrame(const std::string &channel);
  std::string createSendFrame(const std::string &channel, const Event &event, const std::string &filename);
  std::string createDisconnectFrame();

  // Process received frames
  void processFrame(const std::string &frame);

  // Generate summary file
  void writeSummary(const std::string &gameName, const std::string &user,
                    const std::string &filepath);

  bool shouldTerminate();
  const std::string &getUsername() const;
};
