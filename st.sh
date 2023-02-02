#!/usr/bin/env sh

./coursier launch org.scalablytyped.converter:cli_2.12:1.0.0-beta41 -- \
  --outputPackage lucuma.typed \
  --organization edu.gemini.lucuma-typed \
  -f scalajs-react --scala 3.2.2
