# Introduction

This guide is intended to provide an introduction to the capabilities of the various FOLIO compatible Inventory modules provided here.

# General

## Links and Hypermedia

Many of the resources provided by these modules use links to express relationships with other resources (including themselves).

They are represented by a links object, where the name of each field is the relationship and the value the absolute URL to that resource.

### Relationships

In order to know what kind of resource to expect when following a link, known relationships are defined below.

| Relationship | Description | Notes |
|:------------:|-------------|----------|
| `self` | How another party can refer to this resource | Required by all non-root resources |

# API Documentation

API documentation is provided in the form of RAML and JSON.Schema files in the doc/api directory.

This can be used to generate HTML and Markdown documentation (in doc/api/generated), by running `./generate-documentation` from the root directory.
