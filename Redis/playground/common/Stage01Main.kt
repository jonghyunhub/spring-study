package io.jonghyun.Redis.playground.common

import java.net.ServerSocket

fun main() {
    val serverSocket = ServerSocket(6379) // 해당 포트로 소켓 바인딩 TCP 연결 대기
    serverSocket.reuseAddress = true // 서버 재시작시 해당 소켓 끊어버림

    val clientSocket = serverSocket.accept() // 여기서 TCP 연결하기 까지 Blocking => TCP 연결 이후 클라이언트 소켓 반환
    println("클라이언트 연결됨: ${clientSocket.inetAddress}")
}
