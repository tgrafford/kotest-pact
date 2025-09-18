package io.grafford.kotest.pact

import au.com.dius.pact.consumer.ConsumerPactBuilder
import io.kotest.assertions.shouldFailWithMessage
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.future.await
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class PactConsumerExtensionTest : ShouldSpec({
    should("test pact").config(extensions = listOf(PactConsumerExtension(pact))) {
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create(mockServer.getUrl()).resolve("/hello"))
            .POST(HttpRequest.BodyPublishers.ofString("""{"name": "harry"}"""))
            .build()

        val result = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()

        result.statusCode() shouldBe 200
        result.body() shouldBe """{"hello": "harry"}"""
    }

    should("fail if assertions fail").config(extensions = listOf(PactConsumerExtension(pact))) {
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create(mockServer.getUrl()).resolve("/hello"))
            .POST(HttpRequest.BodyPublishers.ofString("""{"name": "harry"}"""))
            .build()

        val result = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()

        result.statusCode() shouldBe 200
        shouldFailWithMessage(
            """
            Contents did not match exactly, but found the following partial match(es):
            Match[0]: part of slice with indexes [0..10] matched actual[0..10]
            Line[0] ="{"hello": "harry"}"
            Match[0]= +++++++++++-------
            expected:<{"hello": "ron"}> but was:<{"hello": "harry"}>
            """.trimIndent()
        ) {
            result.body() shouldBe """{"hello": "ron"}"""
        }
    }

    should("fail if pact fail").config(
        extensions = listOf(
            ExpectFailureExtension(
                """
                The following mismatched requests occurred:
                body - $.name: Expected 'ron' (String) to be equal to 'harry' (String)
                """.trimIndent()
            ),
            PactConsumerExtension(pact)
        )
    ) {
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create(mockServer.getUrl()).resolve("/hello"))
            .POST(HttpRequest.BodyPublishers.ofString("""{"name": "ron"}"""))
            .build()

        val result = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()

        result.statusCode() shouldBe 500
    }

    should("throw when getting mockServer url without PactConsumerExtension") {
        shouldThrowExactly<IllegalStateException> {
            mockServer.getUrl()
        }
    }
})

private class ExpectFailureExtension(private val expectedMessage: String) : TestCaseExtension {
    override suspend fun intercept(
        testCase: TestCase,
        execute: suspend (TestCase) -> TestResult
    ): TestResult {
        return when (val testResult = execute(testCase)) {
            is TestResult.Error -> testResult
            is TestResult.Failure if testResult.cause.message == expectedMessage -> TestResult.Success(testResult.duration)
            is TestResult.Failure -> TestResult.Failure(
                testResult.duration,
                AssertionError("expected message <$expectedMessage> but was <${testResult.cause.message}>")
            )

            is TestResult.Ignored -> testResult
            is TestResult.Success -> TestResult.Failure(testResult.duration, AssertionError("expected failure"))
        }
    }
}

private val pact = ConsumerPactBuilder
    .consumer("Some Consumer")
    .hasPactWith("Some Provider")
    .uponReceiving("a request to say Hello")
    .path("/hello")
    .method("POST")
    .body("""{"name": "harry"}""")
    .willRespondWith()
    .status(200)
    .body("""{"hello": "harry"}""")
    .toPact()