package io.jonghyun.Redis.playground.common

import java.net.ServerSocket

class Stage01Main

fun main() {
    val serverSocket = ServerSocket(6379)
    serverSocket.reuseAddress = true

    val clientSocket = serverSocket.accept()
    println("클라이언트 연결됨: ${clientSocket.inetAddress}")
}
