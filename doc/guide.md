# Introduction

This guide is intended to provide an introduction to the capabilities of the various Folio compatible Knowledge Base and Cataloguing modules provided here.

# General

## Links and Hypermedia

Many of the resources provided by these modules use links to express relationships with other resources (including themselves).

They are represented by an links object, where the name of each field is the relationship and the value the absolute URL to that resource. 

For example, the root of the knowledge base core module provides something similar to:

```
{
  "message" : "Welcome to the Folio Knowledge Base",
  "links" : {
    "instances" : "http://localhost:9130/knowledge-base/instance"
  }
}
```
Which includes a link to the instances collection.

### Relationships

In order to know what kind of resource to expect when following a link, known relationships are defined below.

| Relationship | Description | Notes |
|:------------:|-------------|----------|
| `self` | How another party can refer to this resource | Required by all none root resources |
| `instances` | Refers to the collection of instances resource, used for searching for and creating new instances ||

