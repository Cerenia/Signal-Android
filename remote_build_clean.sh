#!/bin/bash

# --- REMOTE ANDROID BUILDS ---
#
# This script TURNS OFF remote android builds for your project and make them be executed locally again

rm ~/.gradle/init.d/mirakle.gradle 2>/dev/null
