package io.jonghyun.Redis.playground.common

import java.net.ServerSocket

fun main() {
    val serverSocket = ServerSocket(6379)
    serverSocket.reuseAddress = true

    val clientSocket = serverSocket.accept()
    val reader = clientSocket.getInputStream().bufferedReader()
    val writer = clientSocket.getOutputStream().bufferedWriter()

    while(true){
        val line = reader.readLine() ?: break // 연결 종료 시 null 반환

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

    clientSocket.close()
    serverSocket.close()
}