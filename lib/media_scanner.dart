import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/services.dart';

class MediaScanner {
  /// Define Method Channel
  static const MethodChannel _channel = const MethodChannel('media_scanner');

  /// Saves an image to the gallery
  ///
  /// The *[imageBytes]* parameter can't be null
  ///
  /// The *[quality]* parameter must be a value between 1 and 100
  ///
  // TODO (reiko-dev): verify what happens if we use an already used name for the Image.
  /// The *[name]* parameter is the name before the extension to be used on the stored Image
  static Future<ScannerResultModel> saveImage(
    Uint8List imageBytes, {
    int quality = 80,
    String? name,
    bool returnIOSPath = false,
  }) async {
    assert(
      quality > 0 && quality < 101,
      "The quality value must be between 1 and 100",
    );

    final data = await _channel.invokeMethod(
      'saveImage',
      {
        'imageBytes': imageBytes,
        'quality': quality,
        'name': name,
        'isReturnImagePathOfIOS': returnIOSPath,
      },
    );

    return ScannerResultModel(data);
  }

  /// Saves a file to the gallery.
  /// If the file is a media like Image/Video/Sound it will be indexed on the MediaStore of android
  ///
  /// The *[file]* is a String parameter and must be the path to the stored file
  ///
  // TODO (reiko-dev): verify what happens if we use an already used name for the file.
  /// The *[name]* parameter is the name to be used on the file
  static Future<ScannerResultModel> saveFile(
    String file, {
    String? name,
    bool returnIOSPath = false,
  }) async {
    final data = await _channel.invokeMethod(
      'saveFile',
      {'file': file, 'name': name, 'isReturnPathOfIOS': returnIOSPath},
    );
    return ScannerResultModel(data);
  }
}

class ScannerResultModel {
  final bool isSuccess;
  final String? filePath;
  final String? errorMessage;

  ScannerResultModel._({
    required this.errorMessage,
    required this.filePath,
    required this.isSuccess,
  });

  factory ScannerResultModel(dynamic data) {
    final map = Map<String, dynamic>.from(data);

    return ScannerResultModel._(
      errorMessage: map["errorMessage"],
      filePath: map["filePath"],
      isSuccess: map["isSuccess"],
    );
  }

  @override
  String toString() {
    return "isSuccess: $isSuccess, filePath: $filePath, errorMessage: $errorMessage";
  }
}
