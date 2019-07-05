import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import spock.lang.Specification

class EiffelactoryTest extends Specification {
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
        links.add(new Link(Link.Type.ARTIFACT, UUID.fromString("aaaaaaaa-bbbb-5ccc-8ddd-eeeeeeeeeee1")))
        links.add(new Link(Link.Type.CONTEXT, UUID.fromString("aaaaaaaa-bbbb-5ccc-8ddd-eeeeeeeeeee2")))

        when:
        def event = new EiffelArtifactPublishedEvent(meta, data, links)

        then:
        assert event.getMeta().type == "EiffelArtifactPublishedEvent"
        assert event.getMeta().version == "3.0.0"
        assert event.getMeta().getTags() == ["Tag 1", "Tag 2"]
        assert event.getMeta().getSource() == [host: "some host", domainId: "123"] as Source
        assert event.getLinks() == [
                [Link.Type.ARTIFACT, UUID.fromString("aaaaaaaa-bbbb-5ccc-8ddd-eeeeeeeeeee1")] as Link,
                [Link.Type.CONTEXT, UUID.fromString("aaaaaaaa-bbbb-5ccc-8ddd-eeeeeeeeeee2")] as Link
        ]
        assert event.getData().getLocations()[0] == [Location.Type.ARTIFACTORY, "some/artifact/uri"] as Location
    }

    def 'eiffel artifact created event is correctly identified by meta.type and meta.source.name'() {
        setup:
        def dataArtC = [identity: "pkg:job/DEPT/job/USR/job/TEST/job/PRODUCTS/job/NAME/1234/artifacts/filename.txt@1234"]
        def linksArtC = ['']
        def sourceArtC = [name: "JENKINS_EIFFEL_BROADCASTER", host: "jenkins99.some.address"]
        def metaArtC = [
                id: "aaaaaaaa-bbbb-5ccc-8ddd-eeeeeeeeeee2",
                version: "2.0.0",
                time: 123456790,
                type: "EiffelArtifactCreatedEvent",
                source: sourceArtC
        ]
        def messageArtC = [
                data: dataArtC,
                links: linksArtC,
                meta: metaArtC
        ]

        def locationArtP = [type: 'ARTIFACTORY', uri: "some/artifact/uri"]
        def locationsArtP = [locationArtP]
        def dataArtP = [
                locations: locationsArtP
        ]
        def linksArtP = ['aaaaaaaa-bbbb-5ccc-8ddd-eeeeeeeeeee5']
        def metaArtP = [
                id: "aaaaaaaa-bbbb-5ccc-8ddd-eeeeeeeeeee3",
                version: "3.0.0",
                time: 123456790,
                type: "EiffelArtifactPublishedEvent"
        ]
        def messageArtP = [
                data: dataArtP,
                links: linksArtP,
                meta: metaArtP
        ]

        when:
        def fakeArtCMessage = new JsonBuilder(messageArtC).toString()
        def fakeArtPMessage = new JsonBuilder(messageArtP).toString()

        then:
        assert EiffelEventParser.isEiffelArtifactCreatedEvent(fakeArtCMessage)
        assert !EiffelEventParser.isEiffelArtifactCreatedEvent(fakeArtPMessage)
        assert EiffelEventParser.isSentFromJenkins(fakeArtCMessage)
        assert !EiffelEventParser.isSentFromJenkins(fakeArtPMessage)
    }

    def 'parse data needed from ArtC'() {
        setup:
        def dataArtC = [identity: "pkg:job/DEPT/job/USR/job/TEST/job/FOO/job/BAR_BAR/1234/artifacts/some_file.txt@1234"]
        def linksArtC = ['']
        def sourceArtC = [name: "JENKINS_EIFFEL_BROADCASTER", host: "jenkins99.some.address"]
        def metaArtC = [
                id: "aaaaaaaa-bbbb-5ccc-8ddd-eeeeeeeeeee2",
                version: "2.0.0",
                time: 123456790,
                type: "EiffelArtifactCreatedEvent",
                source: sourceArtC
        ]
        def messageArtC = [
                data: dataArtC,
                links: linksArtC,
                meta: metaArtC
        ]
        def fakeArtCMessage = new JsonBuilder(messageArtC).toString()

        when:
        def artC = new JsonSlurper().parseText(fakeArtCMessage) as Map

        def parsedIdentity = [:]
        if (EiffelEventParser.isSentFromJenkins(fakeArtCMessage)) {
            parsedIdentity = EiffelEventParser.parseJenkinsIdentityPurl(artC.data.identity as String)
        }

        def buildUrl = EiffelEventParser.createBuildUrl(artC.meta.source.host as String, parsedIdentity.url as String)

        then:
        assert parsedIdentity.fileName == "some_file.txt"
        assert parsedIdentity.buildNumber == "1234"
        assert parsedIdentity.url == "job/DEPT/job/USR/job/TEST/job/FOO/job/BAR_BAR/1234/"
        assert buildUrl == "https://" + artC.meta.source.host + "/jenkins/" + parsedIdentity.url
        assert artC.meta.id == "aaaaaaaa-bbbb-5ccc-8ddd-eeeeeeeeeee2"
    }
}
