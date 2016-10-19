(ns mambobox-core.protocols)

(defprotocol MusicPersistence
  (add-song [_ song-file-id id3-info])
  (update-song-artist [_ song-id new-artist-name])
  (update-song-album [_ song-id new-album-name])
  (update-song-name [_ song-id new-song-name])

  (add-song-tag [_ song-id tag])
  (remove-song-tag [_ song-id tag])
  (get-song-by-id [_ song-id]))

(defprotocol UserPersistence
  (add-device [_ device-info user-id])
  (update-user-nick [_ user-id nick])
  (get-user-by-device-uuid [_ device-uniq-id]))

(defprotocol SongTracker
  (track-song-view [_ song-id]))

(defprotocol SongSearch
  (search-songs-by-str [_ str])
  (search-albums-by-str [_ str])
  (search-artists-by-str [_ str]))
