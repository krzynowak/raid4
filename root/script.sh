#!/bin/sh

javac RAID.java && java RAID

rm $(find . -name "*.class")
