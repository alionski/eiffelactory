import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.ToString
import groovy.transform.EqualsAndHashCode
import org.artifactory.fs.FileInfo
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.fs.ItemInfo
import org.artifactory.search.Searches
import org.artifactory.search.aql.AqlResult
// This has to be placed as a .jar under /lib
// TODO: import  and compile later via Maven?
import rabbitmqeiffelactory.*

class Constants {
    // This should obviously be "artifactory-uri" later...
    public static final String ARTIFACTORY_URI = "localhost:8081/artifactory/"
    public static final String EIFFEL_ARTIFACT_CREATED_EVENT = "EiffelArtifactCreatedEvent"
    public static final String VERSION_3_0_0 = "3.0.0"
    public static final String JENKINS_EIFFEL_BROADCASTER = "JENKINS_EIFFEL_BROADCASTER"

    public static final String BUILD_URL_FORMAT = "https://%s/jenkins/%s"
    public static final String ARTIFACT_PATH_FORMAT = ARTIFACTORY_URI + "%s/%s/%s"

    public static final String AQL_QUERY_NAME_BUILD_NUMBER = 'items.find(' +
            '{"name":"%s", "artifact.module.build.number":"%s"}).include("name", "repo", "path")'

    public static final String AQL_QUERY_NAME_BUILD_URL = 'items.find(' +
            '{"name":"%s", "artifact.module.build.url":"%s"}).include("name", "repo", "path")'
}

/**
 * Represents an Eiffel link
 * @param type the link type.
 * @param target UUID of target, corresponds to meta.id of the target event.
 */
@ToString
@EqualsAndHashCode
class Link  {
    Type type
    UUID target

    Link(Type type, UUID target) {
        this.type = type
        this.target = target
    }

    // Legal link types
    enum Type {
        ARTIFACT,
        CAUSE,
        CONTEXT,
        FLOW_CONTEXT
    }
}

/**
 * Represents an Eiffel location
 * @param type The type of location.
 * @param uri The URI at which the artifact can be retrieved.
 */
@ToString
@EqualsAndHashCode
class Location {
    Type type
    String uri

    Location(Type type, String uri) {
        this.type = type
        this.uri = uri
    }

    // Legal location types
    enum Type {
        ARTIFACTORY,
        NEXUS,
        OTHER,
        PLAIN
    }
}

/**
 * Represents an Eiffel source
 * @param domainId Identifies the domain that produced an event.
 * @param host The hostname of the event sender.
 * @param name The name of the event sender.
 * @param serializer The identity of the serializer software used to construct the event, in purl format.
 * @param uri The URI of, related to or describing the event sender.
 */
@ToString
@EqualsAndHashCode
class Source {
    String domainId = "domain-id-string"
    String host
    String name = "Artifactory"
    String serializer
    String uri = "artifactory-uri"
}

/**
 * Abstract class representing Eiffel meta data, all event meta classes must extend this class.
 * @param id The unique identity of the event, generated at event creation.
 * @param time The event creation timestamp, in milliseconds.
 * @param type The type of event.
 * @param version The version of the event type.
 * @param tags Tags or keywords associated with the events, for searchability purposes.
 * @param source The source of the event.
 */
@ToString
@EqualsAndHashCode
abstract class Meta {
    // Required default values
    UUID id = UUID.randomUUID()
    long time = System.currentTimeMillis()
    String type = this.class.getSimpleName().split("Meta")[0]

    // Required
    String version

    // Optional
    List<String> tags
    Source source

    Meta(Map optional = [:], String version) {
        this.version = version
        this.tags = optional.tags as List<String>
        this.source = optional.source as Source
    }
}

interface EiffelEvent {
    Meta getMeta()
    List<Link> getLinks()
}

@ToString
@EqualsAndHashCode
class EiffelArtifactPublishedEvent implements EiffelEvent {
    EiffelArtifactPublishedEventMeta meta
    EiffelArtifactPublishedEventData data
    List<Link> links

    EiffelArtifactPublishedEvent(EiffelArtifactPublishedEventMeta meta,
                                 EiffelArtifactPublishedEventData data,
                                 List<Link> links) {
        this.meta = meta
        this.data = data
        this.links = links
    }
}

class EiffelArtifactPublishedEventMeta extends Meta {

    EiffelArtifactPublishedEventMeta(Map optional = [:]) {
        super(optional, Constants.VERSION_3_0_0)
    }

    @Override
    String toString() {
        return type + super.toString()
    }
}

@ToString
@EqualsAndHashCode
class EiffelArtifactPublishedEventData {
    List<Location> locations

    EiffelArtifactPublishedEventData(List<Location> locations) {
        this.locations = locations
    }
}

/**
 * Class with helper methods for handling json data
 */
class JsonHelper {
    /**
     * Removes null values and empty lists from json data
     * @param json The json string to clean
     * @return a json string with all null values and empty lists removed
     */
    static String cleanJson(String json) {
        def jsonMap = new JsonSlurper().parseText(json)
        return JsonOutput.toJson(removeNullFromJsonObject(jsonMap))
    }

    /**
     * Recursively removes all null values and empty collections from objects representing json data
     * @param jsonObject The object representing the json data
     */
    private static def removeNullFromJsonObject(Object jsonObject) {
        if (jsonObject instanceof Map) {
            jsonObject.collectEntries {key, value ->
                if (value) {
                    [(key): removeNullFromJsonObject(value)]
                }
                else [:]
            }
        }
        else if (jsonObject instanceof Collection) {
            jsonObject.collect { removeNullFromJsonObject(it) }.findAll { it != null }
        }
        else {
            jsonObject
        }
    }
}

class EiffelEventParser {
    static boolean isEiffelArtifactCreatedEvent(String message) {
        def jsonObject = new JsonSlurper().parseText(message) as Map

        if (!jsonObject.containsKey("meta")) {
            return false
        }

        def meta = jsonObject.get("meta") as Map
        if (!meta.containsKey("type")) {
            return false
        }

        return jsonObject.meta.type == Constants.EIFFEL_ARTIFACT_CREATED_EVENT
    }

    static boolean isSentFromJenkins(String message) {
        def jsonObject = new JsonSlurper().parseText(message) as Map

        if (!jsonObject.containsKey("meta")) {
            return false
        }

        def meta = jsonObject.get("meta") as Map
        if (!meta.containsKey("source")) {
            return false
        }

        def source = meta.get("source") as Map
        if (!source.containsKey("name")) {
            return false
        }

        return jsonObject.meta.source.name == Constants.JENKINS_EIFFEL_BROADCASTER
    }

    static Map parseJenkinsIdentityPurl(String identity) {
        def parts = identity.split("@")
        def buildNumber = parts[1]
        def filename = parts[0].substring(identity.lastIndexOf("/") + 1)
        parts = parts[0].split("pkg:")
        def url = parts[1].substring(0, parts[1].lastIndexOf("/") - "artifacts".length())

        return [buildNumber: buildNumber, fileName: filename, url: url]
    }

    static String createBuildUrl(String host, String identityUrl) {
        return String.format(Constants.BUILD_URL_FORMAT, host, identityUrl)
    }
}

class EiffelEventListener extends ScriptCallback {
    def static path = "/var/log/eiffelactory/groovy_script_received_jenkins.log"
    File eiffelLogfile = new File(path)

    void deliverEiffelMessage(String message) {
        if (!EiffelEventParser.isEiffelArtifactCreatedEvent(message)) {
            return
        }

        def event = new JsonSlurper().parseText(message) as Map

        def artifacts = new AQLQuery().findArtifactsByNameAndBuildNumber(
                event.name as String, event.buildNumber as String)

        if (artifacts.size() != 1) {
            // This should not be possible
            // TODO: Log to file if this happens
            return
        }

        def artifactPublishedEvent = EiffelArtifactPublishedEventCreator.createEiffelArtifactPublishedEvent(
                UUID.fromString(event.meta.id as String), artifacts[0])

        RabbitMQHelper.publish(artifactPublishedEvent)

    }
}

/**
 * Class for communicating with RabbitMQ
**/
class RabbitMQHelper {
    // TODO: create /var/log/eiffelactory here
    def static path = "/var/log/eiffelactory/groovy_script_sent.log"
    static File logfile = new File(path)
    static RecvMQ receiver = new RecvMQ(new EiffelEventListener())
    static SendMQ sender = new SendMQ()
    static volatile stopped = false


    static def publish(EiffelEvent eiffelEvent) {
        String json = JsonHelper.cleanJson(JsonOutput.toJson(eiffelEvent))
        sender.send(json)

        String timestamp = new Date().format("yyyyMMdd-HH:mm:ss.SSS", TimeZone.getTimeZone('UTC'))
        logfile.append(timestamp + ":" + json + "\n")
    }

    static def startReceiver() {
        receiver.startReceiving()
    }

    static def stopReceiver() {
        receiver.stopReceiving()
    }

    static def stopSender() {
        sender.stopSending()
    }
}

class EiffelArtifactPublishedEventCreator {
    static EiffelArtifactPublishedEvent createEiffelArtifactPublishedEvent(UUID linkId, AQLQuery.ArtifactInfo artifact) {
        def locationUri = createArtifactPath(artifact.repo, artifact.path, artifact.name)
        def locations = [new Location(Location.Type.ARTIFACTORY, locationUri)] as ArrayList<Location>
        def links = [new Link(Link.Type.ARTIFACT, linkId)] as ArrayList<Link>
        def tags = [artifact.repo, artifact.path, artifact.name] as ArrayList<String>

        EiffelArtifactPublishedEvent event = new EiffelArtifactPublishedEvent(
                new EiffelArtifactPublishedEventMeta(tags: tags, source: new Source()),
                new EiffelArtifactPublishedEventData(locations), links)

        return event
    }

    private static String createArtifactPath(String repo, String path, String name) {
        return String.format(Constants.ARTIFACT_PATH_FORMAT, repo, path, name)
    }
}

class AQLQuery {
    class ArtifactInfo {
        String repo
        String path
        String name
    }

    List<ArtifactInfo> findArtifactsByNameAndBuildNumber(String name, String buildNumber) {
        def aqlQuery = String.format(Constants.AQL_QUERY_NAME_BUILD_NUMBER, name, buildNumber)
        return findArtifacts(aqlQuery)
    }

    List<ArtifactInfo> findArtifactsByNameAndBuildUrl(String name, String buildUrl) {
        def aqlQuery = String.format(Constants.AQL_QUERY_NAME_BUILD_URL, name, buildUrl)
        return findArtifacts(aqlQuery)
    }

    List<ArtifactInfo> findArtifacts(String aqlQuery) {
        List<ArtifactInfo> items = []
        searches.aql(aqlQuery) { AqlResult result ->
            result.each {Map item ->
                items.add(new ArtifactInfo(name: item.name, repo: item.repo, path: item.path))
            }
        }

        return items
    }
}

RabbitMQHelper.startReceiver()

/**
 * Handle after create events.
 *
 * Closure parameters:
 * item (org.artifactory.fs.ItemInfo) - the original item being created.
 */
storage {
    afterCreate { item ->
        toFile(item as ItemInfo)
    }
}

/**
 * Executions triggered by REST api
 */
executions {
    stopThreads() {
        if (!RabbitMQHelper.stopped) {
            RabbitMQHelper.stopReceiver()
            RabbitMQHelper.stopSender()
        }
        RabbitMQHelper.stopped = true
    }
}

def getMetadata(String repoKey) {
    File storageLog = new File("/tmp/storage_metadata.log")
    // <domain_query>.find(<criteria>).include(<fields>).sort(<order_and_fields>).offset(<offset_records>).limit(<num_records>)
    ((Searches) searches).aql(
        "items.find({\"repo\": \"" + repoKey + "\"}).include(\"name\", \"repo\", \"property.*\")") {
        AqlResult result ->
            result.each {
                storageLog.append(JsonOutput.prettyPrint(JsonOutput.toJson(result)))
            }
    }
}

def toFile(ItemInfo item) {
    File f = new File("/tmp/upload_metadata.log")
    def created = item.getCreated() as String
    def createdBy = item.getCreatedBy()
    def id = item.getId() as String
    def lastModified = item.getLastModified() as String
    def lastUpd = item.getLastUpdated() as String
    def modifiedBy = item.getModifiedBy() as String
    def name = item.getName()
    def relPath = item.getRelPath()
    def repoKey = item.getRepoKey()
    def repoPath = item.getRepoPath().toPath()
    def isFolder = item.isFolder() as String

    f.write("Name: " + name + "\n" +
            "Created: " + created + "\n" +
            "Created by: " + createdBy  + "\n" +
            "Id: " + id  + "\n" +
            "Last mod: " + lastModified  + "\n" +
            "last upd: " + lastUpd + "\n" +
            "Mod by: " + modifiedBy  + "\n" +
            "Rel path: " + relPath  + "\n" +
            "Repo key: " + repoKey  + "\n" +
            "Repo path: " + repoPath  + "\n" +
            "is folder: " + isFolder + "\n" )

    if (item instanceof FileInfo) {
        def fileInfo = item as FileInfo
        def sha256 = fileInfo.getSha2()
        def sha1 = fileInfo.getSha1()
        def md5 = fileInfo.getMd5()
        def mime = fileInfo.getMimeType()
        def age = fileInfo.getAge() as String
        def size = fileInfo.getSize()

        f.append("sha256: " + sha256 + "\n" +
                "sha1: " + sha1 + "\n" +
                "md5: " + md5 + "\n" +
                "Mime: " + mime + "\n" +
                "Age: "  + age + "\n" +
                "Size: " + size + "\n")
    }

    if (item instanceof FileLayoutInfo) {
        def fileLayout = item as FileLayoutInfo

        f.append("base revision: " + fileLayout.getBaseRevision() + "\n" +
                "Classifier : " + fileLayout.getClassifier() + "\n" +
                "Ext : " + fileLayout.getExt() + "\n")
    }

    getMetadata(repoKey)
}

