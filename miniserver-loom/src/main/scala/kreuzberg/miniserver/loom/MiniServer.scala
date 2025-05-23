package kreuzberg.miniserver.loom

import kreuzberg.miniserver.{DeploymentType, Index, MiniServerConfig}
import org.slf4j.{Logger, LoggerFactory}
import ox.Ox
import sttp.model.{Header, Headers, MediaType, StatusCode}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.{NettyConfig, NettySocketConfig}
import sttp.tapir.server.netty.sync.{NettySyncServer, NettySyncServerBinding}
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler.OnDecodeFailure.*

class MiniServer(config: MiniServerConfig[Identity]) {
  val logger: Logger = LoggerFactory.getLogger(getClass)

  /** Run and block forever. */
  def run(): Unit = {
    import ox.*
    // Also see call of NettySyncServer.startAndWait(..)
    supervised {
      useInScope(start())(_.stop()).discard
      never
    }
  }

  /** Just start the server. */
  def start(fastShutdown: Boolean = false)(using Ox): NettySyncServerBinding = {
    val endpoints = List(
      assetHandler,
      indexHandler
    ) ++ apiEndpointHandler.toList

    val docEndpoints: List[ServerEndpoint[Any, Identity]] = SwaggerInterpreter()
      .fromServerEndpoints[Identity](endpoints, "MiniServer", "0.1")

    val socketConfig = NettySocketConfig.default.withReuseAddress

    val maybeFastshutdownNettyConfig: NettyConfig => NettyConfig = if (fastShutdown) { cfg =>
      cfg.noGracefulShutdown.withDontShutdownEventLoopGroupOnClose // hacky
    } else {
      identity
    }

    logger.info(s"Will start on port ${config.port} (mode=${config.deployment.deploymentType})")
    val server = NettySyncServer()
      .host(config.host)
      .port(config.port)
      .modifyConfig(_.socketConfig(socketConfig))
      .modifyConfig(maybeFastshutdownNettyConfig)
      .addEndpoints(endpoints)
      .addEndpoints(if (config.deployment.deploymentType == DeploymentType.Debug) docEndpoints else Nil)
      .addEndpoints(
        List(
          otherIndexHandler,
          rootAssetHandler
        )
      )

    server.start()
  }

  private val assetHandler     = AssetHandler(Some("assets"), config.deployment.assetPaths, config.deployment).assetHandler
  private val rootAssetHandler = AssetHandler(None, config.deployment.rootAssets, config.deployment).assetHandler

  val indexEndpoint: PublicEndpoint[Unit, Unit, String, Any] = {
    endpoint.get
      .in("")
      .out(htmlBodyUtf8)
      .out(header(Header.contentType(MediaType.TextHtml)))
  }

  private val indexHandler = indexEndpoint.handleSuccess { _ =>
    makeIndexHtml()
  }

  val otherIndexEndpoint: PublicEndpoint[List[String], StatusCode, String, Any] = {
    endpoint.get
      .in(
        paths
          .validate(Validator.custom[List[String]] { paths =>
            ValidationResult.validWhen(paths.lastOption.exists { elem =>
              val suffix = elem.indexOf('.') match {
                case -1 => None
                case n  => Some(elem.drop(n + 1))
              }
              suffix.forall(_ == "html")
            })
          })
          .onDecodeFailureNextEndpoint
      )
      .errorOut(statusCode)
      .out(htmlBodyUtf8)
      .out(header(Header.contentType(MediaType.TextHtml)))
  }

  private val otherIndexHandler = otherIndexEndpoint.handle { _ =>
    Right(makeIndexHtml())
  }

  private def makeIndexHtml(): String = {
    val initData = config.init.map(_.apply())
    Index(config.deployment).pageHtml(initData)
  }

  val apiEndpointHandler = config.api.map(ApiHandler(_).handler)
}
