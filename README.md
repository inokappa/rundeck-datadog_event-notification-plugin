## Rundeck Datadog Notification Plugin

### Installation

Copy the groovy script to the plugins directory:

```sh
```

### Configuration

The plugin requires one configuration entry.

* subject: This string will be set as the description for the generated incident.
* api_key: This is the API Key to your Datadog API.

Configure the service_key in your project configuration by
adding an entry like so: $RDECK_BASE/projects/{project}/etc/project.properties

```sh
```

Or configure it at the instance level: $RDECK_BASE/etc/framework.properties

```sh
```
