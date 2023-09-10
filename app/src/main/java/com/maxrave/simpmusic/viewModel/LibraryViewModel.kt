package com.maxrave.simpmusic.viewModel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.map
import com.maxrave.simpmusic.common.DownloadState
import com.maxrave.simpmusic.data.db.entities.AlbumEntity
import com.maxrave.simpmusic.data.db.entities.ArtistEntity
import com.maxrave.simpmusic.data.db.entities.LocalPlaylistEntity
import com.maxrave.simpmusic.data.db.entities.PlaylistEntity
import com.maxrave.simpmusic.data.db.entities.SongEntity
import com.maxrave.simpmusic.data.model.browse.album.Track
import com.maxrave.simpmusic.data.repository.MainRepository
import com.maxrave.simpmusic.extension.toSongEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject


@HiltViewModel
class LibraryViewModel @Inject constructor(private val mainRepository: MainRepository, application: Application) : AndroidViewModel(application) {
    private var _listRecentlyAdded: MutableLiveData<List<Any>> = MutableLiveData()
    val listRecentlyAdded: LiveData<List<Any>> = _listRecentlyAdded

    private var _listPlaylistFavorite: MutableLiveData<List<Any>> = MutableLiveData()
    val listPlaylistFavorite: LiveData<List<Any>> = _listPlaylistFavorite

    private var _listDownloadedPlaylist: MutableLiveData<List<Any>> = MutableLiveData()
    val listDownloadedPlaylist: LiveData<List<Any>> = _listDownloadedPlaylist

    private var _listLocalPlaylist: MutableLiveData<List<LocalPlaylistEntity>> = MutableLiveData()
    val listLocalPlaylist: LiveData<List<LocalPlaylistEntity>> = _listLocalPlaylist

    private var _songEntity: MutableLiveData<SongEntity> = MutableLiveData()
    val songEntity: LiveData<SongEntity> = _songEntity

//    val recentlyAdded = mainRepository.getAllRecentData().map { pagingData ->
//        pagingData.map { it }
//    }.cachedIn(viewModelScope)
    fun getRecentlyAdded() {
        viewModelScope.launch {
            val temp: MutableList<Any> = mutableListOf<Any>()
            mainRepository.getAllRecentData().collect {data ->
                temp.addAll(data)
                _listRecentlyAdded.postValue(temp)
            }
        }
    }

    fun getPlaylistFavorite() {
        viewModelScope.launch {
            mainRepository.getLikedAlbums().collect { album ->
                val temp: MutableList<Any> = mutableListOf<Any>()
                temp.addAll(album)
                mainRepository.getLikedPlaylists().collect { playlist ->
                    temp.addAll(playlist)
                    val sortedList = temp.sortedWith<Any>(Comparator { p0, p1 ->
                        val timeP0: LocalDateTime? = when (p0) {
                            is AlbumEntity -> p0.inLibrary
                            is PlaylistEntity -> p0.inLibrary
                            else -> null
                        }
                        val timeP1: LocalDateTime? = when (p1) {
                            is AlbumEntity -> p1.inLibrary
                            is PlaylistEntity -> p1.inLibrary
                            else -> null
                        }
                        if (timeP0 == null || timeP1 == null) {
                            return@Comparator if (timeP0 == null && timeP1 == null) 0 else if (timeP0 == null) -1 else 1
                        }
                        timeP0.compareTo(timeP1) // Sort in descending order by inLibrary time
                    })
                    _listPlaylistFavorite.postValue(sortedList)
                }
            }
        }
    }

    fun getLocalPlaylist() {
        viewModelScope.launch {
            mainRepository.getAllLocalPlaylists().collect { values ->
                _listLocalPlaylist.postValue(values)
            }
        }
    }

    fun getDownloadedPlaylist() {
        viewModelScope.launch {
            val temp: MutableList<Any> = mutableListOf<Any>()
            mainRepository.getAllDownloadedPlaylist().collect{ values ->
                temp.addAll(values)
                _listDownloadedPlaylist.postValue(temp)
            }
        }
    }

    fun getSongEntity(videoId: String) {
        viewModelScope.launch {
            mainRepository.getSongById(videoId).collect { values ->
                _songEntity.value = values
            }
        }
    }

    fun updateLikeStatus(videoId: String, likeStatus: Int) {
        viewModelScope.launch {
            mainRepository.updateLikeStatus(likeStatus = likeStatus, videoId = videoId)
        }
    }

    fun createPlaylist(title: String) {
        viewModelScope.launch {
            val localPlaylistEntity = LocalPlaylistEntity(title = title)
            mainRepository.insertLocalPlaylist(localPlaylistEntity)
            getLocalPlaylist()
        }
    }

    fun updateLocalPlaylistTracks(list: List<String>, id: Long) {
        viewModelScope.launch {
            mainRepository.getSongsByListVideoId(list).collect { values ->
                var count = 0
                values.forEach { song ->
                    if (song.downloadState == DownloadState.STATE_DOWNLOADED){
                        count++
                    }
                }
                mainRepository.updateLocalPlaylistTracks(list, id)
                Toast.makeText(getApplication(), "Added to playlist", Toast.LENGTH_SHORT).show()
                if (count == values.size) {
                    mainRepository.updateLocalPlaylistDownloadState(DownloadState.STATE_DOWNLOADED, id)
                }
                else {
                    mainRepository.updateLocalPlaylistDownloadState(DownloadState.STATE_NOT_DOWNLOADED, id)
                }
            }
        }
    }
}