# agent-mapping

![](https://img.shields.io/github/v/release/hmrc/agent-mapping)

## What is agent-mapping and why is it needed?

An Agent acts on behalf of one or more clients. To do so they require a valid relationship to be in place. 
As Agents move to MDTP, our intention where possible is that their Agent/Client relationships that exist in HoDs are honoured. 

The Mapping journey has arisen due to the need to honour SA relationships that are stored within CESA. 
The relationships are not stored in a normalised data store that can be queried. In order to understand if an Agent has a relationship with a client,
we have to retrieve the client record and check if the CESA Agent Ref is present. 
A prerequisite for this check is knowing the CESA Agent Ref, this is captured and linked to the Agents ARN via the Mapping journey.

## API

### check if current logged in user has any enrolments that are eligible for mapping
    GET  /agent-mapping/mappings/eligibility

responses:

    200 OK
    {
        "hasEligibleEnrolments" : true | false
    }

    401 UNAUTHORIZED    if user is not authenticated (missing bearer token or no active session) or does not have agent affinity

### create mapping between ARN and available identifiers

    PUT  /agent-mapping/mappings/arn/:arn
   
path parameters:   
    
    :arn - AgentReferenceNumber
                   
examples:
    
    PUT /agent-mapping/mappings/arn/AARN0000002
    
responses:

    201 CREATED         if successfully captured and mapped one or more identifiers to the ARN
    401 UNAUTHORIZED    if user is not authenticated (missing bearer token or no active session)
    403 FORBIDDEN       if user is not authorised to use the endpoint (either has no identifiers eligible for mapping, not logged in with GG auth provider, or does not have agent affinity)
    409 CONFLICT        if all available identifiers has been already mapped
    
### create mapping between UTR and eligible enrolments before agent subscription

    PUT  /agent-mapping/mappings/pre-subscription/utr/:utr
   
path parameters:   
    
    :utr - SA UTR
                   
examples:
    
    PUT /agent-mapping/mappings/pre-subscription/utr/2000000000
    
responses:

    201 CREATED         if successfully captured and mapped one or more identifiers to the UTR
    401 UNAUTHORIZED    if user is not authenticated (missing bearer token or no active session)
    403 FORBIDDEN       if user is not authorised to use the endpoint (either has no identifiers eligible for mapping, not logged in with GG auth provider, or does not have agent affinity)
    409 CONFLICT        if all available identifiers has been already mapped    
                   
### update mapping between UTR and eligible enrolments to a valid ARN after agent subscription

    PUT  /agent-mapping/mappings/post-subscription/utr/:utr
   
path parameters:   
    
    :utr - SA UTR
                   
examples:
    
    PUT /agent-mapping/mappings/post-subscription/utr/2000000000
    
responses:

    200 OK
    401 UNAUTHORIZED    if user is not authenticated (missing bearer token or no active session)
    403 FORBIDDEN       if user is not authorised to use the endpoint (eg: insufficient enrolments)

### delete mapping between UTR and eligible enrolments before agent subscription
    
    DELETE  /agent-mapping/mappings/pre-subscription/utr/:utr
    
path parameters:   
    
    :utr - SA UTR
                   
examples:
    
    DELETE /agent-mapping/mappings/pre-subscription/utr/2000000000
    
responses:

    204 NO_CONTENT
    401 UNAUTHORIZED    if user is not authenticated (missing bearer token or no active session)    
                       
### find SA mappings for the given ARN

    GET /agent-mapping/mappings/sa/:arn
    
responses:

    200 OK 
    {
        "mappings":[
            { "arn" : "AARN0000002", "saAgentReference" : "A1111A" },
            { "arn" : "AARN0000002", "saAgentReference" : "A1111B" }
        ]
    }
    
    404 NOT FOUND
    
### find VAT mappings for the given ARN

    GET /agent-mapping/mappings/vat/:arn   
    
responses:

    200 OK 
    {
        "mappings":[
            { "arn" : "AARN0000002", "vrn" : "101747696" },
            { "arn" : "AARN0000002", "vrn" : "101747641" }
        ]
    } 
    
    404 NOT FOUND
    
### find AgentCode mappings for the given ARN

    GET /agent-mapping/mappings/agentcode/:arn   
    
responses:

    200 OK 
    {
        "mappings":[
            { "arn" : "AARN0000002", "agentCode" : "101747696" },
            { "arn" : "AARN0000002", "agentCode" : "101747641" }
        ]
    } 
    
    404 NOT FOUND


### find other identifier mappings for the given ARN

    GET /agent-mapping/mappings/key/:key/arn/:arn  
    
supported keys: `char`,`gts`,`mgd`,`novrn`,`ct`,`paye`,`sdlt`
    
responses:

    200 OK 
    {
        "mappings":[
            { "arn" : "AARN0000002", "identifier" : "XYZ00000" },
            { "arn" : "AARN0000002", "identifier" : "ABCD9999" }
        ]
    } 
    
    404 NOT FOUND 
    

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
