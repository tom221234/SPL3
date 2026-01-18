# 1. Clean Java server build
cd /workspaces/Assignment\ 3\ SPL/server
mvn clean

# 2. Clean C++ client build  
cd /workspaces/Assignment\ 3\ SPL/client
make clean

# 3. Delete database (fresh start)
rm /workspaces/Assignment\ 3\ SPL/data/stomp_server.db



# Build server
cd /workspaces/Assignment\ 3\ SPL/server
mvn compile

# Build client
cd /workspaces/Assignment\ 3\ SPL/client
make

# Start Python SQL (creates fresh database)
cd /workspaces/Assignment\ 3\ SPL/data
python3 sql_server.py 7778

# Start Java server (new terminal)
cd /workspaces/Assignment\ 3\ SPL/server
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.stomp.StompServer" -Dexec.args="7777 reactor"

# Start client (new terminal)
cd /workspaces/Assignment\ 3\ SPL/client
./bin/StompWCIClient

Client Commands - Complete Guide:

login 127.0.0.1:7777 citam 123
join Germany_Japan
exit Germany_Japan
report data/events1_partial.json
summary Germany_Japan citam output.txt
logout