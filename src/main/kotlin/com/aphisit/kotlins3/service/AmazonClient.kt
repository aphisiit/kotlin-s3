package com.aphisit.kotlins3.service

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.*
import com.aphisit.kotlins3.utils.convertInputStreamToStreamResponseBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.*
import java.util.*
import java.util.UUID
import java.util.function.Consumer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.collections.ArrayList


@Service
class AmazonClient {

    val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    @Autowired
    private lateinit var amazonS3 : AmazonS3

    @Value("\${amazonProperties.endpointUrl}")
    private lateinit var endpointUrl: String

    @Value("\${amazonProperties.bucketName}")
    private lateinit var bucketName: String

    @Throws(IOException::class)
    private fun convertMultiPartToFile(file: MultipartFile): File {
        val convFile = File(file.originalFilename)
        val fos = FileOutputStream(convFile)
        fos.write(file.bytes)
        fos.close()
        return convFile
    }

    private fun uploadFileTos3bucket(fileName: String, file: File) {
        amazonS3.putObject(PutObjectRequest(bucketName, fileName, file)
                .withCannedAcl(CannedAccessControlList.PublicRead))
    }

    fun uploadFile(multipartFile: MultipartFile): String? {
        var fileUrl = ""
        try {
            val file: File = convertMultiPartToFile(multipartFile)
            val fileName: String = multipartFile.originalFilename!!
            fileUrl = "$endpointUrl/$bucketName/$fileName"
            uploadFileTos3bucket(fileName, file)
            file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return fileUrl
    }

    fun listAllObject(bucket: String? = bucketName) : ObjectListing {
        logger.info("list in bucket : $bucket")
        return amazonS3.listObjects(bucket)
    }

    fun objectDoseExits(name: String? = null) : String {
        return "file name $name does exits : ${amazonS3.doesObjectExist(bucketName, name)}"
    }

    fun checkingFileExitsInList(list: List<String>) : List<String> {
        val result = ArrayList<String>()
        for(l in list) {
            if(amazonS3.doesObjectExist(bucketName, l)){
                result.add(l)
                logger.info("fileName : $l is exits on S3")
            }
        }
        return result
    }

    fun downloadSingleObject(objectName: String? = null) : StreamingResponseBody? {
        return try {
            val finalObject = amazonS3.getObject(bucketName, objectName)?.objectContent
            logger.info("get ObjectName : $objectName success")
            finalObject?.let { convertInputStreamToStreamResponseBody(it) }!!
        }catch (e: Exception) {
            logger.error("something went wrong with ojectName : $objectName")
            logger.error("error : ${e.message}")
            null
        }
    }

    fun deleteObject(objectName: String? = null) : String {
        return try {
            amazonS3.deleteObject(bucketName, objectName)
            "Delete file $bucketName/$objectName is successfully"
        }catch (e: Exception) {
            logger.error("${e.message}")
            logger.error("${e.stackTrace}")
            "Can't delete $bucketName/$objectName cause ${e.message}"
        }
    }

    fun getS3Files(fileNames: List<String?>): List<S3Object>? {
        val s3Objects: MutableList<S3Object> = ArrayList()
        fileNames.forEach(Consumer { fileName: String? ->
            s3Objects.add(
                    amazonS3.getObject(bucketName, fileName))
        })
        return s3Objects
    }

    fun getS3File(fileName: String?): S3Object? {
//        val file = InputStream()
        return amazonS3.getObject(bucketName, fileName)
    }

    fun downloadObject(objectName: List<String?>) : StreamingResponseBody {
        return  downloadAsZipFiles(objectName)
    }

    fun downloadAsZipFiles(files: List<String?>) : StreamingResponseBody {
        val buffer = ByteArray(1024)
        logger.info("file zip name : ${UUID.randomUUID()}.zip")
        val fileZipName = UUID.randomUUID().toString()
        val tempZipFile: File = File.createTempFile(fileZipName, ".zip")

        try {
            FileOutputStream(tempZipFile).use{ fileOutputStream  ->
                ZipOutputStream(fileOutputStream).use { zipOutputStream ->
                    for (upload in files) {
                        logger.info("fileName : $upload")
                        val inputStream = convertS3ObjectToInputStream(upload) ?: continue

                        val zipEntry = ZipEntry(upload)
                        zipOutputStream.putNextEntry(zipEntry)
                        writeStreamToZip(buffer, zipOutputStream, inputStream)
                        inputStream.close()

                        logger.info("archive objectName : $upload success")
                    }
                    zipOutputStream.closeEntry()
                    zipOutputStream.close()
                }
            }

            return convertInputStreamToStreamResponseBody(FileInputStream(tempZipFile))


        }catch (e: Exception) {
            logger.error("${e.message}")
            e.printStackTrace()
            return StreamingResponseBody {}
        }
    }

    fun deleteFolderWithAWSSDK(prefixPath: String) {
        // Todo : Delete file with AWS SDK
        logger.info("Delete start : ${Date()}")
        try{
            var keys: List<DeleteObjectsRequest.KeyVersion>
            val listObjectRequest = ListObjectsV2Request().withBucketName(bucketName).withPrefix(prefixPath)
            var objectListing : ListObjectsV2Result

            do {
                objectListing = amazonS3.listObjectsV2(listObjectRequest)
                keys = ArrayList<DeleteObjectsRequest.KeyVersion>()

                for (x in objectListing.objectSummaries) {
                    keys.add(DeleteObjectsRequest.KeyVersion(x.key))
                }
                logger.info("keys length : ${keys.size}")
                val deleteObjectsRequest = DeleteObjectsRequest(bucketName).withKeys(keys)

                amazonS3.deleteObjects(deleteObjectsRequest)

            }while (objectListing.objectSummaries.size > 0)

        }catch (e: Exception) {
            logger.error(e.message)
        }
        logger.info("Delete end : ${Date()}")
    }

    fun deleteFolderWithAWSCLI(prefixPath: String) {
        // Todo : Delete file with AWS CLI
        logger.info("Delete start : ${Date()}")
        try{
            val process = Runtime.getRuntime().exec("aws s3 rm s3://$bucketName/$prefixPath --recursive")
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
            var temp: String
            while(bufferedReader.readLine().also { temp = it } != null){
                logger.info(temp)
            }
        }catch (e: Exception){
            logger.error(e.message)
        }
        logger.info("Delete end : ${Date()}")
    }


    @SuppressWarnings("Duplicates")
    fun convertS3ObjectToInputStream(objectName: String?) : InputStream? {

        val finalObject : S3ObjectInputStream

        try {
            finalObject = amazonS3.getObject(bucketName, objectName)?.objectContent ?: return null
        }catch (e: Exception) {
            logger.error("something went wrong with objectName : $objectName")
            logger.error("error : ${e.message}")
            return null
        }

        val outputStream = ByteArrayOutputStream()
        var numberOfBytesToWrite = 0
        val data = ByteArray(1024)
        while (finalObject?.read(data, 0, data.size).also {
                    if (it != null) {
                        numberOfBytesToWrite = it
                    }
                } != -1) {
            logger.debug("Writing some bytes..")
            outputStream.write(data, 0, numberOfBytesToWrite)
        }
        finalObject?.close()

        return ByteArrayInputStream(outputStream.toByteArray())
    }

    private fun writeStreamToZip(buffer: ByteArray, zipOutputStream: ZipOutputStream,
                                 inputStream: InputStream) {
        var len: Int
        while (inputStream.read(buffer).also { len = it } > 0) {
            zipOutputStream.write(buffer, 0, len)
        }
    }
}