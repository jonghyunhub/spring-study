package io.jonghyun.Redis.playground.common

import java.net.ServerSocket
import kotlin.concurrent.thread

fun main() {
    val serverSocket = ServerSocket(6379)
    serverSocket.reuseAddress = true

    println("Redis 서버 시작: 포트 6379")

    while (true) {
        val clientSocket = serverSocket.accept()
        println("새 클라이언트 연결: ${clientSocket.inetAddress}")

        // 각 클라이언트를 별도 스레드에서 처리
        thread {
            handleClient(clientSocket)
        }
    }

}

fun handleClient(clientSocket: java.net.Socket) {
    val reader = clientSocket.getInputStream().bufferedReader()
    val writer = clientSocket.getOutputStream().bufferedWriter()

    try {
        while (true) {
            val line = reader.readLine() ?: break

            // 숫자만 잘라내기 (*1 -> 1)
            val arraySize = line.substring(1).toInt()

            val args = mutableListOf<String>()

            // 개수만큼 반복해서 읽어버리기
            // PING이면 1번, COMMAND면 2번 돌면서 다 읽어서 소비
            (0 until arraySize).forEach { i ->
                reader.readLine() // $길이 (예: $7) -> 읽고 버림
                val arg = reader.readLine() // 실제 값 (예: COMMAND, PING)
                args.add(arg)
            }

            val command = args[0].uppercase()
            println("Received Command: $command") // 디버깅용

            when (command) {
                "PING" -> {
                    writer.write("+PONG\r\n")
                    writer.flush()
                }
                "COMMAND" -> {
                    // redis-cli가 접속시 보내는 'COMMAND DOCS' 처리
                    // 이걸 처리 안하면 cli가 응답을 기다리느라 멈춥니다.
                    // 일단 '+OK'라고 거짓말이라도 해줘야 cli가 넘어갑니다.
                    writer.write("+OK\r\n")
                    writer.flush()
                }
            }
        }
    } catch (e: Exception) {
        println("클라이언트 처리 중 오류: ${e.message}")
    } finally {
        clientSocket.close()
        println("클라이언트 연결 종료")
    }
}
