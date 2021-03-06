package net.md_5.bungee.conf;

import com.google.common.base.Preconditions;
import gnu.trove.map.TMap;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import lombok.Getter;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.Favicon;
import net.md_5.bungee.api.ProxyConfig;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ConfigurationAdapter;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.util.CaseInsensitiveMap;
import net.md_5.bungee.util.CaseInsensitiveSet;

/**
 * Core configuration for the proxy.
 */
@Getter
public class Configuration implements ProxyConfig
{

    /**
     * Time before users are disconnected due to no network activity.
     */
    private int timeout = 30000;
    /**
     * UUID used for metrics.
     */
    private String uuid = UUID.randomUUID().toString();
    /**
     * Set of all listeners.
     */
    private Collection<ListenerInfo> listeners;
    /**
     * Set of all servers.
     */
    private TMap<String, ServerInfo> servers;
    /**
     * Should we check minecraft.net auth.
     */
    private boolean onlineMode = true;
    /**
     * Whether we log proxy commands to the proxy log
     */
    private boolean logCommands;
    private boolean logPings = true;
    private int remotePingCache = -1;
    private int playerLimit = -1;
    private Collection<String> disabledCommands;
    private int serverConnectTimeout = 5000;
    private int remotePingTimeout = 5000;
    private int throttle = 4000;
    private int throttleLimit = 3;
    private boolean ipForward;
    private Favicon favicon;
    private int compressionThreshold = 256;
    private String customServerName = "HexaCord";
    private boolean alwaysHandlePackets = false;
    private boolean preventProxyConnections;
    private boolean forgeSupport = true; // Waterfall: default to enabled
    private int pluginChannelLimit = 128;

    public void load()
    {
        ConfigurationAdapter adapter = ProxyServer.getInstance().getConfigurationAdapter();
        adapter.load();

        File fav = new File( "server-icon.png" );
        if ( fav.exists() )
        {
            try
            {
                favicon = Favicon.create( ImageIO.read( fav ) );
            } catch ( IOException | IllegalArgumentException ex )
            {
                ProxyServer.getInstance().getLogger().log( Level.WARNING, "Could not load server icon", ex );
            }
        }

        listeners = adapter.getListeners();
        timeout = adapter.getInt( "timeout", timeout );
        uuid = adapter.getString( "stats", uuid );
        onlineMode = adapter.getBoolean( "online_mode", onlineMode );
        logCommands = adapter.getBoolean( "log_commands", logCommands );
        logPings = adapter.getBoolean( "log_pings", logPings );
        remotePingCache = adapter.getInt( "remote_ping_cache", remotePingCache );
        playerLimit = adapter.getInt( "player_limit", playerLimit );
        serverConnectTimeout = adapter.getInt( "server_connect_timeout", serverConnectTimeout );
        remotePingTimeout = adapter.getInt( "remote_ping_timeout", remotePingTimeout );
        throttle = adapter.getInt( "connection_throttle", throttle );
        throttleLimit = adapter.getInt( "connection_throttle_limit", throttleLimit );
        ipForward = adapter.getBoolean( "ip_forward", ipForward );
        compressionThreshold = adapter.getInt( "network_compression_threshold", compressionThreshold );
        customServerName = adapter.getString( "custom_server_name", "HexaCord" );
        alwaysHandlePackets = adapter.getBoolean( "always_handle_packets", false );
        preventProxyConnections = adapter.getBoolean( "prevent_proxy_connections", preventProxyConnections );
        forgeSupport = adapter.getBoolean( "forge_support", forgeSupport );
        pluginChannelLimit = adapter.getInt( "registered_plugin_channels_limit", pluginChannelLimit );

        disabledCommands = new CaseInsensitiveSet( (Collection<String>) adapter.getList( "disabled_commands", Arrays.asList( "disabledcommandhere" ) ) );

        Preconditions.checkArgument( listeners != null && !listeners.isEmpty(), "No listeners defined." );

        Map<String, ServerInfo> newServers = adapter.getServers();
        Preconditions.checkArgument( newServers != null && !newServers.isEmpty(), "No servers defined" );

        if ( servers == null )
        {
            servers = new CaseInsensitiveMap<>( newServers );
        } else
        {
            Map<String, ServerInfo> oldServers = this.servers;

            for ( ServerInfo oldServer : oldServers.values() )
            {
                ServerInfo newServer = newServers.get( oldServer.getName() );
                if ( ( newServer == null || !oldServer.getAddress().equals( newServer.getAddress() ) ) && !oldServer.getPlayers().isEmpty() )
                {
                    BungeeCord.getInstance().getLogger().info( "Moving players off of server: " + oldServer.getName() );
                    // The server is being removed, or having it's address changed
                    for ( ProxiedPlayer player : oldServer.getPlayers() )
                    {
                        ListenerInfo listener = player.getPendingConnection().getListener();
                        String destinationName = newServers.get( listener.getDefaultServer() ) == null ? listener.getDefaultServer() : listener.getFallbackServer();
                        ServerInfo destination = newServers.get( destinationName );
                        if ( destination == null )
                        {
                            BungeeCord.getInstance().getLogger().severe( "Couldn't find server " + listener.getDefaultServer() + " or " + listener.getFallbackServer() + " to put player " + player.getName() + " on" );
                            player.disconnect( BungeeCord.getInstance().getTranslation( "fallback_kick", "Not found on reload" ) );
                            continue;
                        }
                        player.connect( destination, (success, cause) ->
                        {
                            if ( !success )
                            {
                                BungeeCord.getInstance().getLogger().log( Level.WARNING, "Failed to connect " + player.getName() + " to " + destination.getName(), cause );
                                player.disconnect( BungeeCord.getInstance().getTranslation( "fallback_kick", cause.getCause().getClass().getName() ) );
                            }
                        } );
                    }
                } else
                {
                    // This server isn't new or removed, we'll use bungees behavior of just ignoring
                    // any changes to info outside of the address, this is not ideal, but the alternative
                    // requires resetting multiple objects of which have no proper identity
                    newServers.put( oldServer.getName(), oldServer );
                }
            }
            this.servers = new CaseInsensitiveMap<>( newServers );
        }

        for ( ListenerInfo listener : listeners )
        {
            for ( int i = 0; i < listener.getServerPriority().size(); i++ )
            {
                String server = listener.getServerPriority().get( i );
                Preconditions.checkArgument( servers.containsKey( server ), "Server %s (priority %s) is not defined", server, i );
            }
            for ( String server : listener.getForcedHosts().values() )
            {
                if ( !servers.containsKey( server ) )
                {
                    ProxyServer.getInstance().getLogger().log( Level.WARNING, "Forced host server {0} is not defined", server );
                }
            }
        }
    }

    @Override
    @Deprecated
    public String getFavicon()
    {
        return getFaviconObject().getEncoded();
    }

    @Override
    public Favicon getFaviconObject()
    {
        return favicon;
    }

    @Override
    public String getCustomServerName()
    {
        return customServerName;
    }

    @Override
    public boolean getAlwaysHandlePackets()
    {
        return alwaysHandlePackets;
    }
}
