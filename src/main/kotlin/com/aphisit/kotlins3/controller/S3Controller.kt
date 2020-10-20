package com.aphisit.kotlins3.controller

import com.amazonaws.services.s3.model.ObjectListing
import com.aphisit.kotlins3.service.AmazonClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

@RestController
@RequestMapping("/s3")
class S3Controller {

    @Autowired
    lateinit var amazonClient : AmazonClient

    val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    @PostMapping("/checkFileList")
    fun checkingFileExitsInList(@RequestBody list: List<String>) : List<String> {
        logger.info("dataList : ${list.size}")
        return amazonClient.checkingFileExitsInList(list)
    }

    @PostMapping("/downloadFileList")
    fun downloadFileList(@RequestBody list: List<String>) : StreamingResponseBody {
        logger.info("dataList : ${list.size}")
        return amazonClient.downloadAsZipFiles(list)
    }

    @PostMapping("/uploadFile")
    fun uploadFile(@RequestPart(value = "file") file: MultipartFile): String? {
        return amazonClient.uploadFile(file)
    }

    @GetMapping("/")
    fun listBucket() : ObjectListing {
        return  amazonClient.listAllObject()
    }

    @GetMapping("/fileNameExits")
    fun fileNameDoseExit(@RequestParam("objectName") objectName: String) : String {
        return amazonClient.objectDoseExits(objectName)
    }

    @GetMapping("/downloadSingleObject")
    fun downloadSingleObject(@RequestParam("objectName") objectName: String) : StreamingResponseBody? {
        return amazonClient.downloadSingleObject(objectName)
    }

//    @DeleteMapping("/deleteFile")
//    fun deleteFile(@RequestPart(value = "url") fileUrl: String): String? {
//        return amazonClient.deleteFileFromS3Bucket(fileUrl)
//    }

    @DeleteMapping("/deleteObject")
    fun deleteObject(@RequestParam("objectName") objectName: String) : String {
        return amazonClient.deleteObject(objectName)
    }

    @DeleteMapping("/deleteFolder")
    fun deleteFolder() : String? {
        try {
            amazonClient.deleteFolder();
            return "OK"
        }catch (e: Exception){
            return e.message
        }
    }

 }