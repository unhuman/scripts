#!/bin/zsh

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <version1> <version2>"
    exit 1
fi

version1=$1
version2=$2

git fetch --tags && git log $version1..$version2 | grep -v "Merge pull request" | grep -v "Merge branch" | grep '^[[:space:]]\{4\}[A-Za-z0-9][[A-Za-z0-9]*-[0-9][0-9]*' | grep -o '[A-Za-z0-9][[A-Za-z0-9]*-[0-9][0-9]*' | sort | uniq
