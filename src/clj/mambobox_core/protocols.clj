(ns mambobox-core.protocols)

(defprotocol MusicPersistence
  (add-song [_ song-file-id id3-info user-id])
  (update-song-artist [_ song-id new-artist-name user-id])
  (update-song-album [_ song-id new-album-name user-id])
  (update-song-name [_ song-id new-song-name user-id])
  (add-song-tag [_ song-id tag user-id])
  (remove-song-tag [_ song-id tag user-id])
  (get-song-by-id [_ song-id]))

(defprotocol UserPersistence
  (add-device [_ device-info user-id])
  (update-user-nick [_ user-id nick])
  (get-user-by-device-uuid [_ device-uniq-id])
  (set-user-favourite-song [_ user-id song-id])
  (unset-user-favourite-song [_ user-id song-id])
  (get-all-user-favourite-songs [_ user-id]))

(defprotocol SongTracker
  (track-song-view [_ song-id user-id]))

(defprotocol SongSearch
  (search-songs-by-str [_ str])
  (hot-songs [_]))
