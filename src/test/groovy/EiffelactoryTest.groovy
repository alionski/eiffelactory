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
}
