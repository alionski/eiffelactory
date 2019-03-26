import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.jfrog.artifactory.client.ArtifactoryClientBuilder
import spock.lang.Specification

class EiffelactoryTest extends Specification {
    def 'plugin is executed'() {
        setup:
            def baseUrl = "http://localhost:8081/artifactory";
            def artifactory = ArtifactoryClientBuilder.create()
                    .setUrl(baseUrl)
                    .setUsername("admin")
                    .setPassword("password").build()

        when:
            def json = new JsonSlurper().parseText(artifactory.plugins().execute('eiffelactory').sync())

        then:
            json.status == 'ok'
    }

    def 'eiffel artifact published event is correctly constructed'() {
        setup:
        def tags = new ArrayList<String>()
        tags.add("Tag 1")
        tags.add("Tag 2")

        def source = new Source(host: "some host", domainId: "123")

        def meta = new EiffelArtifactPublishedEventMeta(tags: tags, source: source)

        def locations = new ArrayList<Location>()
        locations.add(new Location(Location.Type.ARTIFACTORY, "some/artifact/uri"))

        def data = new EiffelArtifactPublishedEventData(locations)

        def links = new ArrayList<Link>()
        links.add(new Link("ARTIFACT", UUID.fromString("aaaaaaaa-bbbb-5ccc-8ddd-eeeeeeeeeee1")))
        links.add(new Link("CONTEXT", UUID.fromString("aaaaaaaa-bbbb-5ccc-8ddd-eeeeeeeeeee2")))

        def event = new EiffelArtifactPublishedEvent(meta, data, links)

        println(JsonOutput.prettyPrint(JsonOutput.toJson(event)))
    }
}
