#!/usr/bin/env python3
"""
Basic Python Server for STOMP Assignment – Stage 3.3

IMPORTANT:
DO NOT CHANGE the server name or the basic protocol.
Students should EXTEND this server by implementing
the methods below.
"""

import socket
import sys
import threading
import sqlite3
import os


SERVER_NAME = "STOMP_PYTHON_SQL_SERVER"  # DO NOT CHANGE!
DB_FILE = "stomp_server.db"              # DO NOT CHANGE!

# Thread-local storage for database connections
_local = threading.local()


def get_db_connection():
    """Get a thread-local database connection."""
    if not hasattr(_local, 'connection'):
        _local.connection = sqlite3.connect(DB_FILE)
        _local.connection.row_factory = sqlite3.Row
    return _local.connection


def recv_null_terminated(sock: socket.socket) -> str:
    data = b""
    while True:
        chunk = sock.recv(1024)
        if not chunk:
            return ""
        data += chunk
        if b"\0" in data:
            msg, _ = data.split(b"\0", 1)
            return msg.decode("utf-8", errors="replace")


def init_database():
    """Initialize the SQLite database with required tables."""
    conn = sqlite3.connect(DB_FILE)
    cursor = conn.cursor()
    
    # Create users table
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS users (
            username TEXT PRIMARY KEY,
            password TEXT NOT NULL,
            registration_date TEXT NOT NULL
        )
    ''')
    
    # Create login_history table
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS login_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL,
            login_time TEXT NOT NULL,
            logout_time TEXT,
            FOREIGN KEY (username) REFERENCES users(username)
        )
    ''')
    
    # Create file_tracking table
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS file_tracking (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL,
            filename TEXT NOT NULL,
            upload_time TEXT NOT NULL,
            game_channel TEXT,
            FOREIGN KEY (username) REFERENCES users(username)
        )
    ''')
    
    conn.commit()
    conn.close()
    print(f"[{SERVER_NAME}] Database initialized: {DB_FILE}")


def execute_sql_command(sql_command: str) -> str:
    """Execute an SQL command (INSERT, UPDATE, DELETE) and return result."""
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute(sql_command)
        conn.commit()
        return f"SUCCESS|rows_affected:{cursor.rowcount}"
    except Exception as e:
        return f"ERROR:{str(e)}"


def execute_sql_query(sql_query: str) -> str:
    """Execute an SQL query (SELECT) and return results."""
    try:
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute(sql_query)
        rows = cursor.fetchall()
        
        if not rows:
            return "SUCCESS|"
        
        # Format results: SUCCESS|row1|row2|...
        result_parts = ["SUCCESS"]
        for row in rows:
            # Format each row as a tuple string
            row_str = str(tuple(row))
            result_parts.append(row_str)
        
        return "|".join(result_parts)
    except Exception as e:
        return f"ERROR:{str(e)}"


def handle_sql(sql_string: str) -> str:
    """Determine if SQL is a query or command and execute accordingly."""
    sql_upper = sql_string.strip().upper()
    
    if sql_upper.startswith("SELECT"):
        return execute_sql_query(sql_string)
    else:
        return execute_sql_command(sql_string)


def handle_client(client_socket: socket.socket, addr):
    print(f"[{SERVER_NAME}] Client connected from {addr}")

    try:
        while True:
            message = recv_null_terminated(client_socket)
            if message == "":
                break

            print(f"[{SERVER_NAME}] Received:")
            print(message)

            # Execute SQL and get response
            response = handle_sql(message)
            print(f"[{SERVER_NAME}] Response: {response}")
            
            client_socket.sendall((response + "\0").encode("utf-8"))

    except Exception as e:
        print(f"[{SERVER_NAME}] Error handling client {addr}: {e}")
    finally:
        try:
            client_socket.close()
        except Exception:
            pass
        print(f"[{SERVER_NAME}] Client {addr} disconnected")


def start_server(host="127.0.0.1", port=7778):
    # Initialize database before starting server
    init_database()
    
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    try:
        server_socket.bind((host, port))
        server_socket.listen(5)
        print(f"[{SERVER_NAME}] Server started on {host}:{port}")
        print(f"[{SERVER_NAME}] Waiting for connections...")

        while True:
            client_socket, addr = server_socket.accept()
            t = threading.Thread(
                target=handle_client,
                args=(client_socket, addr),
                daemon=True
            )
            t.start()

    except KeyboardInterrupt:
        print(f"\n[{SERVER_NAME}] Shutting down server...")
    finally:
        try:
            server_socket.close()
        except Exception:
            pass


if __name__ == "__main__":
    port = 7778
    if len(sys.argv) > 1:
        raw_port = sys.argv[1].strip()
        try:
            port = int(raw_port)
        except ValueError:
            print(f"Invalid port '{raw_port}', falling back to default {port}")

    start_server(port=port)
