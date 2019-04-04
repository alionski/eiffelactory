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
}
