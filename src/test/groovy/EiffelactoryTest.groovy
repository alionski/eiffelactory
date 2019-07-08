import groovy.json.JsonBuilder
import spock.lang.Specification

class EiffelactoryTest extends Specification {
    def 'eiffel artifact published event is correctly created'() {
        setup:
        def artifactInfo = new ArtifactInfo(
                repo: "eiffel-actory-testing", path: "project-1_23", name: "filename.ext")

        when:
        def artP = EiffelEventCreator.createEiffelArtifactPublishedEvent(
                UUID.fromString("aaaaaaaa-bbbb-5ccc-8ddd-eeeeeeeeeee1"), artifactInfo)

        then:
        assert artP.getMeta().type == "EiffelArtifactPublishedEvent"
        assert artP.getMeta().version == Constants.VERSION_3_0_0
        assert artP.getMeta().getTags() == [artifactInfo.repo, artifactInfo.path, artifactInfo.name]

        assert artP.getMeta().getSource() ==
                [domainId: "domain-id-string", name: "Artifactory", uri: Constants.ARTIFACTORY_URI] as Source

        assert artP.getLinks() == [
                [Link.Type.ARTIFACT, UUID.fromString("aaaaaaaa-bbbb-5ccc-8ddd-eeeeeeeeeee1")] as Link,
        ]

        assert artP.getData().getLocations()[0] ==
                [Location.Type.ARTIFACTORY, artifactInfo.getLocationPath()] as Location
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
        def artCMessage = new JsonBuilder(messageArtC).toString()
        def artPMessage = new JsonBuilder(messageArtP).toString()

        then:
        assert EiffelEventParser.isEiffelArtifactCreatedEvent(artCMessage)
        assert !EiffelEventParser.isEiffelArtifactCreatedEvent(artPMessage)
        assert EiffelEventParser.isSentFromJenkins(artCMessage)
        assert !EiffelEventParser.isSentFromJenkins(artPMessage)
    }

    def 'parse identity purl from ArtC sent by EiffelBroadcaster'() {
        setup:
        def identity = "pkg:job/DEPT/job/USR/job/TEST/job/FOO/job/BAR_BAR/1234/artifacts/some_file.txt@1234"

        when:
        def identityInfo = EiffelEventParser.parseJenkinsIdentityPurl(identity)

        then:
        assert identityInfo.fileName == "some_file.txt"
        assert identityInfo.buildNumber == "1234"
        assert identityInfo.url == "job/DEPT/job/USR/job/TEST/job/FOO/job/BAR_BAR/1234/"
    }

    def 'create build url as it appears in build info in Artifactory'() {
        setup:
        def artCHost = "jenkins99.some.address"
        def identityInfo = EiffelEventParser.parseJenkinsIdentityPurl(
                "pkg:job/DEPT/job/USR/job/TEST/job/FOO/job/BAR_BAR/1234/artifacts/some_file.txt@1234")

        when:
        def buildUrl = EiffelEventParser.createBuildUrl(artCHost, identityInfo.url)

        then:
        assert buildUrl == "https://" + artCHost + "/jenkins/" + identityInfo.url
    }
}
