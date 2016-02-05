w4-bpmnplus-service-jamespot
============================

This project is a service implementation for W4 BPMN+ that allows to communicate with [jamespot](http://www.jamespot.com/).

It allows using any API (any function on any object) of Jamespot. Typically it allows to publish an article to any audience.


Installation
------------

### Building from source

Using maven, run 

    mvn package
    
Then extract the generated package, either zip or tar.gz, at the root of a W4 BPMN+ Engine installation. It will create the necessary entries into `services` subdirectory of W4 BPMN+ Engine.


Configuration
-------------

The configuration file is located at the following location `W4BPMPLUS_HOME/services/bpmnplus-service-jamespot/conf/jamespot.properties` 

Required parameters are

- **jamespot.url**: URL of the jamespot instance
- **security.module**: name of the module as declared in the advanced administration page of Jamespot. 
- **security.secret**: shared secret configured in the module key
- **security.ttl**: time to live of API messages sent (expressed in milliseconds)

You can also configure the default API parameters

- **default.object**: default object type to call (article, user, spot, ...)
- **default.[object.]function**: default function to call (create, delete, ...)
- **default.[object.]parameters**: default parameters to give to the API
- **default.[object.]parameter._parameter-name_**: value for the default parameter _parameter-name_

At runtime parameters for each call are taken by priority

- either in DataInput (for ServiceTask) or in the attached elements (for Notifications)
- in **default._object_.parameter._parameter-name_** configuration property
- in **default.parameter._parameter-name_** configuration property


Usage
-----

In BPMN+ Composer, this service can be used either

- as a ServiceTask implementation
- as a notification extension on processes, activities or events

by selecting the service named "Jamespot" with version "1.0".

In ServiceTasks, API parameters must be specified as DataInputs using the BPMN InputOutputSpecification. In Notifications, API parameters must be specified as attached elements. 


License
-------

Copyright (c) 2016, W4 SAS 

This project is licensed under the terms of the MIT License (see LICENSE file)

Ce projet est licencié sous les termes de la licence MIT (voir le fichier LICENSE)
