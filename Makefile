JFLAGS = -g
JC = javac
JVM = java

# Detecteaza automat locatia bibliotecilor Jackson
# Verifica mai multe locatii posibile: ../libs, libs, sau in directorul curent
JACKSON_CORE = $(shell if [ -f ../libs/jackson-core-2.17.0.jar ]; then echo ../libs/jackson-core-2.17.0.jar; elif [ -f libs/jackson-core-2.17.0.jar ]; then echo libs/jackson-core-2.17.0.jar; elif [ -f jackson-core-2.17.0.jar ]; then echo jackson-core-2.17.0.jar; else echo ../libs/jackson-core-2.17.0.jar; fi)
JACKSON_ANNOTATIONS = $(shell if [ -f ../libs/jackson-annotations-2.17.0.jar ]; then echo ../libs/jackson-annotations-2.17.0.jar; elif [ -f libs/jackson-annotations-2.17.0.jar ]; then echo libs/jackson-annotations-2.17.0.jar; elif [ -f jackson-annotations-2.17.0.jar ]; then echo jackson-annotations-2.17.0.jar; else echo ../libs/jackson-annotations-2.17.0.jar; fi)
JACKSON_DATABIND = $(shell if [ -f ../libs/jackson-databind-2.17.0.jar ]; then echo ../libs/jackson-databind-2.17.0.jar; elif [ -f libs/jackson-databind-2.17.0.jar ]; then echo libs/jackson-databind-2.17.0.jar; elif [ -f jackson-databind-2.17.0.jar ]; then echo jackson-databind-2.17.0.jar; else echo ../libs/jackson-databind-2.17.0.jar; fi)
CLASSPATH = .:$(JACKSON_CORE):$(JACKSON_ANNOTATIONS):$(JACKSON_DATABIND)

build:
	$(JC) $(JFLAGS) -cp $(CLASSPATH) Article.java Tema1.java

run:
	$(JVM) -cp $(CLASSPATH) Tema1 $(ARGS)

clean:
	rm -f *.class *.txt