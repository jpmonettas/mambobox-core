(ns mambobox-core.protocols)

(defprotocol MusicPersistence
  
  (get-artist-by-name [_ name])
  (add-artist [_ artist-name])

  (add-album [_ artist-id album-name])
  (get-album-by-name [_ artist-id name])
  
  (add-song [_ song-file-id id3-info user-id])
  (update-song-album [_ song-id album-id])
  (update-song-artist [_ song-id artist-id])
  (add-song-tag [_ song-id tag user-id])
  (get-song [_ song-id]))

(defprotocol UserPersistence
  (add-device [_ device-info user-id])
  (update-user-nick [_ user-id nick])
  (get-user-by-device-uuid [_ device-uniq-id]))

(defprotocol SongTracker
  (track-song-view [_ song-id user-id]))

(defprotocol SongSearch
  (search-songs-by-str [_ str])
  (search-albums-by-str [_ str])
  (search-artists-by-str [_ str]))
