import com.dtolabs.rundeck.plugins.notification.NotificationPlugin;
import com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants;
import com.dtolabs.rundeck.core.plugins.configuration.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode

// See http://docs.datadoghq.com/ja/api/

// curl  -X POST -H "Content-type: application/json" \
// -d '{
//       "title": "Did you hear the news today?",
//       "text": "Oh boy!",
//       "priority": "normal",
//       "tags": ["environment:test"],
//       "alert_type": "info"
//   }' \
// 'https://app.datadoghq.com/api/v1/events?api_key=xxxxxxxxxxxxxxxxxxxxxxxxxx'

class DEFAULTS {
    static String DATADOG_EVENT_URL = "https://app.datadoghq.com/api/v1/events?api_key="
    static String SUBJECT_LINE='Rundeck JOB: ${job.status} [${job.project}] \"${job.name}\" run by ${job.user} (#${job.execid})'
    static String API_KEY='Your Datadog API Key'
}

/**
 * Expands the Subject string using a predefined set of tokens
 */
def titleString(text,binding) {
  //defines the set of tokens usable in the subject configuration property
  def tokens=[
    '${job.status}': binding.execution.status.toUpperCase(),
    '${job.project}': binding.execution.job.project,
    '${job.name}': binding.execution.job.name,
    '${job.group}': binding.execution.job.group,
    '${job.user}': binding.execution.user,
    '${job.execid}': binding.execution.id.toString(),
    '${job.slugged_name}': binding.execution.job.name.replaceAll("[\\s]", "_").toLowerCase()
  ]
  text.replaceAll(/(\$\{\S+?\})/){
    tokens[it[0]]
  }
}

/**
 * Setting Alert Info
**/
def alertInfo(binding) {
  //System.err.println("DEBUG: bindingData="+binding.execution.status)
  switch (binding.execution.status) {
    case "succeeded" :
      alert_info = "info"
      break
    case "failed" :
      alert_info = "error"
      break
    default:
      alert_info = "info"
      break
  }
   return alert_info
}

/**
 * @param execution
 * @param configuration
 */
def triggerEvent(Map execution, Map configuration) {
  //System.err.println("DEBUG: api_key="+configuration.api_key)
  //System.err.println("DEBUG: excutionData="+execution)
  def expandedTitle = titleString(configuration.subject, [execution:execution])
  def expandedAlertinfo = alertInfo([execution:execution])
  def tags = configuration.tags ? titleString(configuration.tags, [execution:execution]) : 'rundeck:' + execution.job.name
  def job_data = [
    title: expandedTitle,
    text: "Please see: " + execution.href,
    tags: tags,
    alert_type: expandedAlertinfo
  ]

  if (configuration.aggregation_key) {
    def aggregationKey = titleString(configuration.aggregation_key, [execution:execution])
    job_data.put("aggregation_key", aggregationKey)
  }

  // Send the request.
  def url = new URL(DEFAULTS.DATADOG_EVENT_URL+configuration.api_key)
  def connection = url.openConnection()
  connection.setRequestMethod("POST")
  connection.addRequestProperty("Content-type", "application/json")
  connection.doOutput = true
  def writer = new OutputStreamWriter(connection.outputStream)
  def json = new ObjectMapper()
  writer.write(json.writeValueAsString(job_data))
  writer.flush()
  writer.close()
  connection.connect()

  // process the response.
  def response = connection.content.text
  //System.err.println("DEBUG: response: "+response)
  JsonNode jsnode= json.readTree(response)
  def status = jsnode.get("status").asText()
  if (! "success".equals(status)) {
      System.err.println("ERROR: DatadogEventNotification plugin status: " + status)
  }
}

/**
 * Main
**/
rundeckPlugin(NotificationPlugin){
    title="DataDog_Event"
    description="Create a Trigger event."
    configuration{
        subject title:"Subject", description:"Incident subject line. Can contain \${job.status}, \${job.project}, \${job.name}, \${job.group}, \${job.user}, \${job.execid}", defaultValue:DEFAULTS.SUBJECT_LINE, required:true
        api_key title:"API Key", description:"Datadog API key", defaultValue:DEFAULTS.API_KEY, required:true
        tags title:"Tags", description:"Datadog tags for this event", defaultValue:"rundeck:\${job.project}:\${job.status}, rundeck:\${job.group}", required:false
        aggregation_key title:"Aggregation key", description: "Aggregation key for this event.", defaultValue: "rundeck:\${job.project}:\${job.group}:\${job.slugged_name}", required: false
    }
    onstart { Map execution, Map configuration ->
        triggerEvent(execution, configuration)
        true
    }
    onfailure { Map execution, Map configuration ->
        triggerEvent(execution, configuration)
        true
    }
    onsuccess { Map execution, Map configuration ->
        triggerEvent(execution, configuration)
        true
    }

}
