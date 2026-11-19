package cn.alittlecookie.lutista.data.model

import cn.alittlecookie.lutista.data.database.entity.FilterEntity

/**
 * 单图精修产生的配方副本约定。
 *
 * 点滤镜精修时会把原配方整份复制一份给这张照片用，名字统一加这个后缀。
 * 后缀既用来在界面上把备份归到「单图备份」分组，也用来从日常滤镜列表
 * （底部横排、整页平铺、首页「滤镜选择」下拉）里过滤掉，避免备份把常用滤镜挤下去。
 */
const val PHOTO_BACKUP_SUFFIX = " · 单图备份"

/** 名字是否属于单图备份（含「名称 · 单图备份2」这类重名后缀） */
fun isPhotoBackupName(name: String): Boolean = name.contains(PHOTO_BACKUP_SUFFIX)

/** 该配方是否属于单图备份 */
val FilterEntity.isPhotoBackup: Boolean
    get() = isPhotoBackupName(name)
