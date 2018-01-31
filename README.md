# agent-mapping

[![Build Status](https://travis-ci.org/hmrc/agent-mapping.svg)](https://travis-ci.org/hmrc/agent-mapping) [ ![Download](https://api.bintray.com/packages/hmrc/releases/agent-mapping/images/download.svg) ](https://bintray.com/hmrc/releases/agent-mapping/_latestVersion)


## What is agent-mapping and why is it needed?

An Agent acts on behalf of one or more clients. To do so they require a valid relationship to be in place. 
As Agents move to MDTP, our intention where possible is that their Agent/Client relationships that exist in HoDs are honoured. 
The Mapping journey has arisen due to the need to honour SA relationships that are stored within CESA. 
The relationships are not stored in a normalised data store that can be queried. In order to understand if an Agent has a relationship with a client, we have to retrieve the client record and check if the CESA Agent Ref is present. 
A prerequisite for this check is knowing the CESA Agent Ref, this is captured and linked to the Agents ARN via the Mapping journey.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
