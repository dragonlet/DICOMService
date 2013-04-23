package test

import java.security.KeyStore
import java.io.FileInputStream
import java.security.KeyStore.PasswordProtection

object GetKeystore {

    def main(args: Array[String]): Unit = {

        try {
            val password = "jjjjjjjjjjjjjjjjjjjjjjjjjjjj"

                val fileName = "D:\\eclipse\\workspace_amqpb\\DICOMService\\src\\main\\resources\\dicomsvc.jks"
                    /*
                    System.setProperty("keystorePath", fileName)
                    System.setProperty("keyPassword", password)
                    System.setProperty("keystoreType", "JKS")
                     */

                    val ks = KeyStore.getInstance("JKS")

                    val fis = new FileInputStream(fileName)
            ks.load(fis, password.toCharArray)
            

            val aliases = ks.aliases
            while (aliases.hasMoreElements) {
                val a = aliases.nextElement
                val entry = ks.getEntry(a, new KeyStore.PasswordProtection(password.toCharArray))
                println("  " + a + " Class: " + entry.getClass);
                //println(entry)
            }

        } catch {
            case e:Exception => e.printStackTrace
        }
    }

}