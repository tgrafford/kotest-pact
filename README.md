# kotest-pact

[Pact](https://pact.io/) integration for Kotest.

## Usage

Add the [Pact JVM consumer](https://github.com/pact-foundation/pact-jvm) and `kotest-pact` to your project:

```kotlin
testImplementation("au.com.dius.pact.consumer:kotlin:{PACT_VERSION}")
testImplementation("io.graffordt.kotest.pact:kotest-pact:{KOTEST_PACT_VERSION}")
```

Add the `PactConsumerExtension` to a test case to validate and create a Pact:

```kotlin
class PactConsumerExtensionTest : ShouldSpec({
    should("test pact").config(
        extensions = listOf(
            PactConsumerExtension(
                ConsumerPactBuilder
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
            )
        )
    ) {
        // given
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder()
            .uri(URI.create(mockServer.getUrl()).resolve("/hello"))
            .POST(HttpRequest.BodyPublishers.ofString("""{"name": "harry"}"""))
            .build()

        // when
        val result = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()

        // then
        result.statusCode() shouldBe 200
        result.body() shouldBe """{"hello": "harry"}"""
    }
}
```

