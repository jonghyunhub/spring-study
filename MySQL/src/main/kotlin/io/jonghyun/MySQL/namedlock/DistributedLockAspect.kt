package io.jonghyun.MySQL.namedlock

import io.jonghyun.MySQL.support.error.CoreException
import io.jonghyun.MySQL.support.error.ErrorType
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Component

@Aspect
@Component
class DistributedLockAspect(
    private val namedLockExecutor: NamedLockExecutor
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
        private val parser = SpelExpressionParser()
        private val parameterNameDiscoverer = DefaultParameterNameDiscoverer()
    }

    @Around("@annotation(distributedLock)")
    fun lock(joinPoint: ProceedingJoinPoint, distributedLock: DistributedLock): Any? {
        val lockKey = generateLockKey(joinPoint, distributedLock)

        logger.info("Attempting to acquire lock: $lockKey")

        return namedLockExecutor.executeWithLock(
            lockKey = lockKey,
            timeout = distributedLock.timeout
        ) {
            joinPoint.proceed()
        }
    }

    private fun generateLockKey(joinPoint: ProceedingJoinPoint, distributedLock: DistributedLock): String {
        val prefix = distributedLock.key

        // dynamicKey가 없으면 prefix만 사용
        if (distributedLock.dynamicKey.isEmpty()) {
            return prefix
        }

        // SpEL 표현식 파싱
        val dynamicValue = parseDynamicKey(joinPoint, distributedLock.dynamicKey)
        return "$prefix:$dynamicValue"
    }

    /**
     * 분산락 임계영역 설정시 범위를 줄이기 위해 동적 키 생성 ex) user:create:1 (user 로 키 설정하면 임계영역이 너무 넓어짐)
     */
    private fun parseDynamicKey(joinPoint: ProceedingJoinPoint, expression: String): String {
        val methodSignature = joinPoint.signature as MethodSignature
        val method = methodSignature.method
        val args = joinPoint.args

        // 파라미터 이름 가져오기
        val parameterNames = parameterNameDiscoverer.getParameterNames(method)
            ?: run {
                logger.error("Cannot discover parameter names for method: ${method.name}")
                throw CoreException(
                    errorType = ErrorType.LOCK_KEY_GENERATION_FAILED,
                    data = mapOf("method" to method.name, "expression" to expression)
                )
            }

        // SpEL 컨텍스트 설정 및 평가
        val context = StandardEvaluationContext().apply {
            parameterNames.forEachIndexed { index, name ->
                setVariable(name, args[index])
            }
        }

        return parser.parseExpression(expression).getValue(context)?.toString()
            ?: run {
                logger.error("SpEL expression evaluated to null: $expression")
                throw CoreException(
                    errorType = ErrorType.LOCK_KEY_GENERATION_FAILED,
                    data = mapOf("expression" to expression, "method" to method.name)
                )
            }


    }
}