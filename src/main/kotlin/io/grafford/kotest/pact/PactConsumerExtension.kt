package io.grafford.kotest.pact

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.PactTestExecutionContext
import au.com.dius.pact.consumer.PactTestRun
import au.com.dius.pact.consumer.PactVerificationResult
import au.com.dius.pact.consumer.model.MockProviderConfig
import au.com.dius.pact.consumer.runConsumerTest
import au.com.dius.pact.core.model.BasePact
import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestScope
import io.kotest.engine.test.TestResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.lang.AssertionError
import kotlin.coroutines.CoroutineContext
import kotlin.time.measureTimedValue

private data object PactMockServerKey : CoroutineContext.Key<PactMockServerElement>
private data class PactMockServerElement(val value: MockServer) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = PactMockServerKey
}

val TestScope.mockServer: MockServer
    get() = coroutineContext[PactMockServerKey]?.value ?: error(
        "Unable to find MockServer in coroutine context." +
                "Are you sure you have added PactConsumerExtension to the list of extensions?"
    )

class PactConsumerExtension(
    private val pact: BasePact,
    private val config: MockProviderConfig = MockProviderConfig.createDefault()
) : TestCaseExtension {
    override suspend fun intercept(testCase: TestCase, execute: suspend (TestCase) -> TestResult): TestResult {
        val (pactResult, duration) = measureTimedValue {
            runConsumerTest(pact, config) {
                when (val testResult = execute(testCase)) {
                    is TestResult.Error -> throw testResult.cause
                    is TestResult.Failure -> throw testResult.cause
                    is TestResult.Ignored -> testResult
                    is TestResult.Success -> testResult
                }
            }
        }
        return when (pactResult) {
            is PactVerificationResult.Error -> TestResult.Error(duration, pactResult.toAssertionError())
            is PactVerificationResult.Ok -> TestResult.Success(duration)
            is PactVerificationResult.ExpectedButNotReceived,
            is PactVerificationResult.Mismatches,
            is PactVerificationResult.PartialMismatch,
            is PactVerificationResult.UnexpectedRequest -> TestResult.Failure(duration, pactResult.toAssertionError())
        }
    }
}

private fun <R> runConsumerTest(
    pact: BasePact,
    config: MockProviderConfig,
    test: suspend () -> R
) = runConsumerTest(
    pact,
    config,
    object : PactTestRun<R> {
        override fun run(mockServer: MockServer, context: PactTestExecutionContext?): R = runBlocking {
            withContext(currentCoroutineContext() + PactMockServerElement(mockServer)) {
                test()
            }
        }
    }
)

private fun PactVerificationResult.toAssertionError() = AssertionError(getDescription())
