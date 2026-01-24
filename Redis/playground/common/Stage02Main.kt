package io.jonghyun.Redis.playground.common

import java.net.ServerSocket

fun main() {
    val serverSocket = ServerSocket(6379)
    serverSocket.reuseAddress  = true

    val clientSocket = serverSocket.accept()
    val reader = clientSocket.getInputStream().bufferedReader()
    val writer = clientSocket.getOutputStream().bufferedWriter()

    // RESP 프로토콜 파싱
    reader.readLine() // *1
    reader.readLine() // $4
    val command = reader.readLine() // PING
    if (command == "PING") {
        val response = "+PONG\r\n"
        writer.write(response)
    }

    writer.flush()
    clientSocket.close()
}