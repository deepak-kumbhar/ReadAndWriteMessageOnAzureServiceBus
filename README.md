# ReadAndWriteMessageOnAzureServiceBus
Read and write messages from Azure service bus.

Before running the file make sure to change following keys,
- **<ENDPOINT_URL>** - Your Azure endpoint URL.
- **<QUEUE_NAME>** - Queue name.


After making changes just run the program as Main method.

**Output:**

```
Message received: 
	MessageId = 0, 
	SequenceNumber = 475, 
	EnqueuedTimeUtc = 2020-03-16T08:30:09.969Z,
	ExpiresAtUtc = 2020-03-16T08:32:09.969Z, 
	ContentType = "application/json",  
	Content: [ firstName = Deepak, lastName = Kumbhar ]
            
Message received: 
	MessageId = 1, 
	SequenceNumber = 476, 
	EnqueuedTimeUtc = 2020-03-16T08:30:10.250Z,
	ExpiresAtUtc = 2020-03-16T08:32:10.250Z, 
	ContentType = "application/json",  
	Content: [ firstName = Yuraj, lastName = Kale ]```
            
            
 
