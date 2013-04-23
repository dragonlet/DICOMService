package test

import scala.xml.PrettyPrinter
import scala.xml.PCData

object LearnXml {

    def main(args: Array[String]): Unit = {
            val prettyPrinter = new PrettyPrinter(1000, 2)

            val tag = "Taggy"
            val content = "Content"
                
            val goo = <Goo>{content}</Goo>.copy(label = tag)
            println("\ngoo:\n" + prettyPrinter.format(goo))
                
            val special = "<&>\"'$".toList
            val text = "aaa+bbb"
                
            if (text.toList.intersect(special).length > 0) println("got special") else println("no special")
            
            def gotSpecial(text:String) = text.toList.intersect(special).length > 0
                        
            val stuff = if (gotSpecial(text)) PCData(text) else text
            
            val foo = <Foo>{if (gotSpecial(text)) PCData(text) else text}</Foo>.copy(label = "Al")
            println("\nfoo:\n" + prettyPrinter.format(foo))

    }

}