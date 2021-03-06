import {EditorContentImageProcessor} from 'idoc/editor/content/editor-content-image-processor';
import {PromiseStub} from 'test/promise-stub';

describe('EditorContentImageProcessor', function () {

  let editorContentImageProcessor;
  let editorContent = `<div>The <img width='100' height='200' data-embedded-id='emf:630f886c-19d2-47ac-bcb8-9fc7872735f7' /> is an old painting.</div>`;
  let editorContentNotPersistedImage = `<div>The <img width='100' height='200' src='http://123.com/img.jpg' /> is an old painting.</div>`;
  let editorContentPastedImage = `<div>The <img width='100' height='200' src='data:image/png;base64' /> is a pasted image.</div>`;
  let onePixelImage = EditorContentImageProcessor.IMAGE_ONE_PIXEL;
  let dynamicElementsRegistry;

  beforeEach(() => {
    let userServiceMock = {
      getCurrentUser: () => {
        return PromiseStub.resolve({
          id: 'john@domain',
          name: 'John',
          username: 'john',
          isAdmin: true,
          language: 'en',
          tenantId: 'tenant.com'
        });
      }
    };

    let contentRestService = {
      getImageUrl: () => {
        return '/remote/api/content/static/emf:630f886c-19d2-47ac-bcb8-9fc7872735f7?tenantId=tenant.com';
      }
    };

    dynamicElementsRegistry = {
      handleImage: sinon.spy()
    };
    editorContentImageProcessor = new EditorContentImageProcessor(userServiceMock, contentRestService, dynamicElementsRegistry);
  });

  it('should set lazy image properties in edit mode', function () {
    let expected = `<div>The <img width="100" height="200" data-embedded-id="emf:630f886c-19d2-47ac-bcb8-9fc7872735f7" data-original="/remote/api/content/static/emf:630f886c-19d2-47ac-bcb8-9fc7872735f7?tenantId=tenant.com" data-cke-saved-src="/remote/api/content/static/emf:630f886c-19d2-47ac-bcb8-9fc7872735f7?tenantId=tenant.com" src="${onePixelImage}"> is an old painting.</div>`;
    editorContentImageProcessor.preprocessContent(getEditor(editorContent, true, false), editorContent).then((result) => {
      expect(result).to.equal(expected);
    });
  });

  it('should set lazy image properties in preview mode', function () {
    let expected = `<div>The <img width="100" height="200" data-embedded-id="emf:630f886c-19d2-47ac-bcb8-9fc7872735f7" data-original="/remote/api/content/static/emf:630f886c-19d2-47ac-bcb8-9fc7872735f7?tenantId=tenant.com" data-cke-saved-src="/remote/api/content/static/emf:630f886c-19d2-47ac-bcb8-9fc7872735f7?tenantId=tenant.com" src="${onePixelImage}"> is an old painting.</div>`;
    editorContentImageProcessor.preprocessContent(getEditor(editorContent, false, true), editorContent).then((result) => {
      expect(result).to.equal(expected);
    });
  });

  it('should also set lazy image properties when image is not persisted', function () {
    let expected = `<div>The <img width="100" height="200" src="data:image/png;base64" data-original="data:image/png;base64" data-cke-saved-src="data:image/png;base64"> is a pasted image.</div>`;
    editorContentImageProcessor.preprocessContent(getEditor(editorContentPastedImage, false, true), editorContentPastedImage).then((result) => {
      expect(result).to.equal(expected);
    });
  });

  it('should not set lazy image properties when is neither preview nor edit mode', function () {
    let expected = '<div>The <img width="100" height="200" src="http://123.com/img.jpg" data-cke-saved-src="http://123.com/img.jpg"> is an old painting.</div>';
    editorContentImageProcessor.preprocessContent(getEditor(editorContentNotPersistedImage, false, false), editorContentNotPersistedImage).then((result) => {
      expect(result).to.equal(expected);
    });

    expected = '<div>The <img width="100" height="200" data-embedded-id="emf:630f886c-19d2-47ac-bcb8-9fc7872735f7" src="/remote/api/content/static/emf:630f886c-19d2-47ac-bcb8-9fc7872735f7?tenantId=tenant.com" data-cke-saved-src="/remote/api/content/static/emf:630f886c-19d2-47ac-bcb8-9fc7872735f7?tenantId=tenant.com"> is an old painting.</div>';
    editorContentImageProcessor.preprocessContent(getEditor(editorContent, false, false), editorContent).then((result) => {
      expect(result).to.equal(expected);
    });
    expect(dynamicElementsRegistry.handleImage.calledTwice).to.be.true;
  });
});

function getEditor(data, isEditMode, isPreviewMode) {
  return {
    fire: () => {
      sinon.spy();
    },
    element: {
      getElementsByTag: () => {
        return sinon.spy();
      }
    },
    context: {
      isEditMode: () => {
        return isEditMode;
      },
      isPreviewMode: () => {
        return isPreviewMode;
      }
    }
  };
}