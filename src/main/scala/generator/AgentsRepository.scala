package generator

import java.sql.{ResultSet, DriverManager, Statement, Connection}
import java.util.UUID

import org.postgresql.Driver

/**
  * Created by ytaras on 10/28/16.
  */
object AgentsRepository {

  def withConnectionDo[T](f: Connection => T): T = {
    Class.forName(classOf[Driver].getCanonicalName)
    val connection = DriverManager.getConnection(
      "jdbc:postgresql:agents", "postgres", ""
    )
    try {
      f(connection)
    } finally {
      connection.close()
    }
  }

  def withStatementDo[T](f: Statement => T) = withConnectionDo { conn =>
    val statement = conn.createStatement()
    try {
      f(statement)
    } finally {
      statement.close()
    }
  }

  def allUuids: Seq[UUID] = withStatementDo { statement =>
    val rs = statement.executeQuery("select agent_id from agent_metadata")
    val buffer = scala.collection.mutable.Buffer[UUID]()
    while(rs.next()) {
      buffer += UUID.fromString(rs.getString(1))
    }
    buffer.toSeq
  }

  def count: Int = withStatementDo { statement =>
    val rs = statement.executeQuery("select count(*) from agent_metadata")
    rs.next()
    rs.getInt(1)
  }

  def populateAgents(max: Int) = {
    val current = count
    if(max > count) {
      withConnectionDo { session =>
        session.setAutoCommit(false)
        val ps = session.prepareStatement("insert into agent_metadata (server_name, server_type, operating_system, created_at, updated_at) values (?, ?, ?, now(), now())")
        // FIXME - This is slow. Instead we should be using batch inserts
        (1 to (max - count)).foreach { x =>
          // FIXME - Semantic generation
          ps.setString(1, s"server_$x")
          ps.setString(2, s"type_$x")
          ps.setString(3, s"os_$x")
          ps.execute()
        }
        session.commit()
      }
    }
  }


}
