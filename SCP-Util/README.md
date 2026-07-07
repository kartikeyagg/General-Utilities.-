A java swing programs. JAVA 17 with mvn . ( no browser but on pc runnable)

Renders a minimalist button . On button click . Go through a dir say Dir1( which can also contain sub dirs) . checks which file has been modified from when it previously ran. 


If a file is modified than scp it to a target location which is mirror of Dir1 . 

The current pc is windows pc , the remote is ubuntu. 

The path of local folder , the mirror folder is specified in a json. The name of both folder are same. 
The remote IP , username and password are also specified in the json file. 

Json file is present in same Dir as the java program file. 

Also the last time the program ran is stored in another json file. 

Dependencies(mvn) :- java swing and google Gson. 



