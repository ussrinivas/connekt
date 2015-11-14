package com.flipkart.connekt.commons.dao

import java.io.IOException

import org.apache.commons.codec.CharEncoding
import org.apache.hadoop.hbase.client.{Get, HTableInterface, Put}

import scala.collection.mutable.ListBuffer

/**
 *
 *
 * @author durga.s
 * @version 11/18/15
 */
trait HbaseDao {

  @throws[IOException]
  def addRow(tableName: String, rowKey: String, data: Map[String, Map[String, Array[Byte]]])(implicit hTableInterface: HTableInterface) = {

    val put: Put = new Put(rowKey.getBytes(CharEncoding.UTF_8))
    data.foreach { case(colFamily, v) =>
      v.foreach { case(colQualifier, data) =>
        put.add(colFamily.getBytes(CharEncoding.UTF_8), colQualifier.getBytes(CharEncoding.UTF_8), data)
      }
    }

    hTableInterface.put(put)
  }

  @throws[IOException]
  def fetchRow(tableName: String, rowKey: String, colFamilies: List[String])(implicit hTableInterface: HTableInterface): Map[String, Map[String, Array[Byte]]] = {

    val get: Get = new Get(rowKey.getBytes(CharEncoding.UTF_8))
    colFamilies.foreach(cF => get.addFamily(cF.getBytes(CharEncoding.UTF_8)))

    val rowResult = hTableInterface.get(get)
    var resultMap = Map[String, Map[String, Array[Byte]]]()

    colFamilies.foreach { cF =>
      val optResult = rowResult.getFamilyMap(cF.getBytes(CharEncoding.UTF_8))

      Option(optResult).map(cFResult => {
        val i = cFResult.keySet().iterator()
        val vList = new ListBuffer[(String, Array[Byte])]()
        val vMap = scala.collection.mutable.Map[String, Array[Byte]]()

        while(i.hasNext) {
          val colQualifier = i.next
          vMap += new String(colQualifier) -> cFResult.get(colQualifier)
        }

        resultMap += cF -> vMap.toMap
      })
    }

    resultMap
  }
}
