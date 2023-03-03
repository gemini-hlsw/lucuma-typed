# lucuma-typed

A repository of Scalably Typed generated facades, and nothing more. The sources are 100% generated, so you won't find much in this repo :)

We use the ST CLI interface, rather than the sbt plugin, to generate the sources. This is so that each target npm package can be published as a separate sbt module while still sharing common dependencies with others.

To invoke the generator run `lucumaTypedGenerate`. This takes several minutes :)

To add a new facade, first add it to the `package.json` and `npm install`. Then in `build.sbt` you should setup sbt subprojects for that facade and all of its (new) transitive dependencies. Some of its transitives (such as `std`) will already be in the build. Additionally, you should wire up the `.dependsOn` relationships between all these new projects, and add them to the root aggregate.
