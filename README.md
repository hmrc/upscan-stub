
# upscan-stub

## Overview

This is a stub service for testing and integration with the HMRC upscan services and AWS. It removes the need to have the full suite of upscan services ([upscan-initiate](https://github.com/hmrc/upscan-initiate), [upscan-verify](https://github.com/hmrc/upscan-verify), [upscan-notify](https://github.com/hmrc/upscan-notify)) running locally, and also replaces the need for local virus scanning, and communication with AWS from a local machine.

#### Where can I use upscan-stub? ####
upscan-stub is intended for *local testing only*. i.e. smoke testing rather than acceptance testing.
For acceptance tests, running in a Jenkins hosted environment, teams are strongly encouraged to test against the real suite of Upscan microservices instead of using upscan-stub. The benefits of this are:
- True reflection of Production functionality
- More test coverage for the Upscan services themselves

upscan-stub does not provide 100% of the functionality available in the Upscan microservices suite. (If it did, we would have effectively rewritten Upscan again!) Specifically, the following functionality is missing from upscan-stub:
- Verification that all required form fields are present when uploading a file
- Observability: metrics and logging
- Enforcement of correct field ordering when uploading files.


If you are unclear about the full functionality of the upscan services (including AWS and ClamAV), please read the documentation [here](https://github.com/hmrc/upscan-initiate#architecture).

By running this service locally, the following interactions can take place:
1. POST to initiate a request and receive JSON parameters for an upload form. This stubs [upscan-initiate](https://github.com/hmrc/upscan-initiate)
2. POST a form to upload a file. The file will be stored in local temporary storage while the application runs. This stubs AWS S3.
3. Receive a callback to a URL passed in during Step 2. The will be triggered automatically via an Actor when Step 2 completes. This stubs [upscan-verify](https://github.com/hmrc/upscan-verify) and [upscan-notify](https://github.com/hmrc/upscan-notify)
4. Download the file POSTED in step 2 from local URL contained in the callback of Step 3. This stubs AWS S3.

Additionally, a specific file can be passed in at Step 2 which will cause the application to quarantine the file and notify of the quarantine in Step 3.

## Usage

### Calling ```upscan-stub```
| Path | Supported Methods | Description |
| ---- | ----------------  | ----------- |
| ```/upscan/initiate``` | POST | Endpoint to retrieve parameters for building upload form. Documented here: [upscan-initiate](https://github.com/hmrc/upscan-initiate) |
| ```/upscan/v2/initiate``` | POST | Endpoint to retrieve parameters for building upload form offering the additional capability to redirect on upload error.  Documented here: [upscan-initiate-v2](https://github.com/hmrc/upscan-initiate#post-upscanv2initiate) |
| ```/upscan/upload``` | POST | Endpoint to upload a file, replacing the endpoint for uploading to an AWS S3 bucket. Documented here: [upscan-initiate](https://github.com/hmrc/upscan-initiate) |
| ```/upscan/download/:reference``` | GET | Retrieve a file from local storage, as uploaded via the ```/upscan/upload``` endpoint |


### Callback response from ```upscan-stub```
Additionally, the service will make a callback in the format documented in [upscan-notify](https://github.com/hmrc/upscan-notify).

### Testing error scenarios using dedicated filename schema
It is possible to easily force different upscan errors by simply renaming the uploaded file according to the following schema:
- `rejected.S3_ERROR_CODE.EXT`, e.g. *rejected.UnexpectedContent.png*, see <https://docs.aws.amazon.com/AmazonS3/latest/API/ErrorResponses.html#ErrorCodeList>
- `infected.VIRUS_NAME.EXT`, e.g. *infected.MyDoom.jpeg*
- `invalid.REASON.EXT`, e.g. *invalid.ZipInDisguise.txt*
- `unknown.REASON.EXT`, e.g. *unknown.SpookyCookie.pdf*

### Testing real virus scanning with ```upscan-stub```
It is possible to test the upload of a virus-infected file using a test file included in the ```upscan-stub``` project. Uploading the following file will trigger a quarantined file callback:
```test/resources/eicar-standard-av-test-file.txt```

##### Sophos Antivirus Scanner collisions
If you're using the [Sophos Home](http://home.sophos.com) antivirus application on your laptop, then you may find the software automatically quarantines the virus infected test file mentioned above.
###### 1. Quarantining virus test resources
Each time you pull from git, Sophos may delete ```test/resources/eicar-standard-av-test-file.txt```.
To avoid this, you can allowlist the file on the [Sophos Dashboard](https://cloud.sophos.com/manage/home).
**Note:** you will need to allowlist the copy of the file built to `/target` as well. i.e. both of:
```
test/resources/eicar-standard-av-test-file.txt
target/scala-2.11/test-classes/eicar-standard-av-test-file.txt
```
###### 2. Failing virus tests
Some of the `upscan-stub` integration tests may also fail due to collisions with Sophos.
Currently there is no workaround other than to disable the tests, or Sophos, when running locally.

## Running locally
Start your ```upscan-stub``` service on port 9570 with the following command: ```sbt "run 9570"```

##### Service Manager
Alternatively, the Service Manager profile for Upscan can be started with:
```
    sm -r --start UPSCAN
```

### Using ```upscan-listener```
The flow of calls relies upon a "listening" endpoint within your service to receive asynchronous notification once your file has been virus scanned and is ready to downloaded within  your service. 
If you require a service to capture callbacks whilst developing your own service, you can use the helper service [```upscan-listener```](https://github.com/hmrc/upscan-listener).
