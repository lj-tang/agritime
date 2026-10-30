/**
 * 拍照复用层（V4 T12）
 * 复用既有 PhotoCapture.open + 后端 /api/recognition，不修改 photo-capture.js。
 */
(function () {
  function analyze(onResult, prefer) {
    if (!window.PhotoCapture) {
      if (onResult) onResult({ error: '拍照组件未加载' });
      return;
    }
    window.PhotoCapture.open(function (cropName, confidence) {
      if (!cropName) {
        if (onResult) onResult({ error: '未识别到作物' });
        return;
      }
      // confidence 可能是 null（用户手动输入的兜底路径），不编造数值
      if (onResult) onResult({ cropName: cropName, confidence: typeof confidence === 'number' ? confidence : null, confirmed: false });
    }, prefer);
  }

  function confirmAndFill(result, fields, onFill) {
    if (!result || result.error) return;
    if (fields && result.cropName) {
      if (fields.cropName) fields.cropName.value = result.cropName;
    }
    if (onFill) onFill(result);
  }

  var api = { analyze: analyze, confirmAndFill: confirmAndFill };

  if (typeof window !== 'undefined') { window.CropPhoto = api; }
  if (typeof module !== 'undefined' && module.exports) { module.exports = api; }
})();