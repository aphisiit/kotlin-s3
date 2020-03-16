# Kotlin-AWS: S3
This Project is build for purpose manipulate download,upload,download multi file as zip file from S3.   
``` port default 9002 ```

## Run application
```shell script
gradle bootRun  
```

## Build Artifact   
```shell script
gradle clean build 
```

## Build Docker Images   

```shell script
docker build . -t username/image_name:tags -f DockerFile    
```

## Run Docker images 
```shell script
docker run -p 9002:9002 --name kotlins3 -d username/image_name:tags
```