set -exu
cd ../../../target/src
kotlinc -jvm-target 1.8 *.kt
javap -p -v -s -constants -c *.class
