#!/bin/sh
#
# Gradle wrapper script for Unix systems
#

GRADLE_OPTS=""
APP_HOME="$(cd "$(dirname "$0")" && pwd)"

exec gradle "$@"
