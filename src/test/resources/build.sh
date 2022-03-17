set -exu
cd ../../../target/src
javac -encoding UTF-8 *.java  -target 8 -source 8
kotlinc -jvm-target 1.8 *.kt
javap -p -v -s -constants -c *.class
