This module is an attempt to create a spike with which we can compare and contrast idiomatic persistence (IE ORMs and their tooling) with the top-down / contract-first model imposed by the raml module builder. Modern persistence tools tend to be built using domain-model-first approach where the java classes defining the storage model come first. Scripting toolsets such as gson templates are then used to provide devs with flexible bindings between those models and, for example, json output formats. By using contract-first raml and generating POJOs we make this approach impractical as the dev spends most of their time writing code to move between these two pojo worlds. In essence, the raml module builder imposes the persistence impedence mismatch down onto the lowest level of the persistence module and makes the persistence module developers life extremely unpleasant.

The goal of interfaces is to protect / insulate devs and hide the implementation, this module then, is an experiment to see what happens when we do not invade the storage module developer life with our raml-builder idioms and leave the raml as the specification of an interface rather than an implementation.


Background::


SpringBoot/virt.x project
https://github.com/songyangster/vertx-in-springboot
